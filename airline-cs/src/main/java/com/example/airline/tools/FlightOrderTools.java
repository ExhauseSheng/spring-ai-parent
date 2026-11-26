package com.example.airline.tools;

import com.example.airline.advisor.OrderAnswerGuardAdvisor;
import com.example.airline.domain.AirlineOrder;
import com.example.airline.mapper.AirlineOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 航空订单 Tools：查询订单 + 退订订单（数据经 MyBatis-Plus 落库）。
 *
 * <h3>退订三重门禁（业务规则在代码层强制，不依赖模型自觉）</h3>
 * <pre>
 * 门禁1 · 查询在先：cancelFlightOrder 校验【本会话】(conversationId 由 toolContext 传入)
 *         是否已通过 queryFlightOrder 查询过该订单——没有则直接拒绝；
 * 门禁2 · 客户确认：查询时记录当时的对话记忆长度作为快照，退订前扫描
 *         【快照之后的用户消息】是否出现确认词（确认/同意/确定/是的/没错）——
 *         同一轮内"查完立刻退"时本轮对话尚未写入记忆，天然被拒，必须跨轮等客户回复；
 * 门禁3 · 状态原子更新：UPDATE ... WHERE status='NORMAL'，已退订订单无法重复退订。
 * </pre>
 * 模型侧的"先展示订单再询问确认"由系统提示词约束，以上三道是代码兜底。
 */
@Component
public class FlightOrderTools {

    private static final Logger log = LoggerFactory.getLogger(FlightOrderTools.class);

    /** toolContext 中携带会话 ID 的 key（与 AirlineChatService 共用） */
    public static final String CONVERSATION_ID_KEY = "conversationId";

    /** 客户确认关键词（只扫描 USER 消息） */
    private static final List<String> CONFIRM_KEYWORDS = List.of("确认", "同意", "确定", "是的", "没错");

    private final AirlineOrderMapper orderMapper;
    private final ChatMemory chatMemory;

    /** 门禁状态：conversationId → 待退订订单（查询后写入，退订成功或失败后清除） */
    private final Map<String, PendingCancel> pendingCancels = new ConcurrentHashMap<>();

    public FlightOrderTools(AirlineOrderMapper orderMapper, ChatMemory chatMemory) {
        this.orderMapper = orderMapper;
        this.chatMemory = chatMemory;
    }

    @Tool(name = "queryFlightOrder",
            description = "按订单编码查询航空订单详情（订单编码、姓名、出发地、目的地、状态）。"
                    + "客户要求退订时必须先调用本工具，并将信息完整展示给客户。")
    public String queryFlightOrder(@ToolParam(description = "订单编码，例如 FL001") String orderNo,
                                   ToolContext toolContext) {
        AirlineOrder order = orderMapper.selectById(orderNo);
        if (order == null) {
            return "查询失败：订单 " + orderNo + " 不存在，请核对订单编码。";
        }

        String conversationId = conversationIdOf(toolContext);
        if (conversationId != null) {
            pendingCancels.put(conversationId, new PendingCancel(orderNo, memorySize(conversationId)));
        }
        // 通知守卫 Advisor：本轮真实查询过（其回复中的订单详情不会被拦截）
        OrderAnswerGuardAdvisor.markQueried();
        log.info("查询订单 {}（会话 {}），已记录退订门禁快照", orderNo, conversationId);

        return "订单编码: " + order.getOrderNo()
                + "\n姓名: " + order.getName()
                + "\n出发地: " + order.getDeparture()
                + "\n目的地: " + order.getDestination()
                + "\n状态: " + order.getStatusText();
    }

    @Tool(name = "cancelFlightOrder",
            description = "退订航空订单（状态由正常变更为已退订）。严格前置条件：本会话已查询过该订单、"
                    + "已向客户完整展示、且客户已明确回复确认；任一条件不满足都会被拒绝执行。")
    public String cancelFlightOrder(@ToolParam(description = "要退订的订单编码") String orderNo,
                                    ToolContext toolContext) {
        String conversationId = conversationIdOf(toolContext);

        // 门禁1：本会话必须先查询过该订单
        PendingCancel pending = conversationId == null ? null : pendingCancels.get(conversationId);
        if (pending == null || !pending.orderNo().equals(orderNo)) {
            return "退订被拒绝：请先调用 queryFlightOrder 查询订单 " + orderNo
                    + "，将完整信息展示给客户并获得客户确认后，再执行退订。";
        }

        // 门禁2：查询之后的对话中，客户必须出现过确认回复
        if (!hasCustomerConfirmation(conversationId, pending.memorySnapshot())) {
            return "退订被拒绝：尚未收到客户对退订的明确确认。请先展示订单信息，待客户回复确认后再执行退订。";
        }

        // 门禁3：仅当状态为"正常"时原子更新，防止重复退订
        // 注意：MP 的 update 需要非空实体作为 SET 子句，传 null 会生成 "UPDATE ... WHERE" 非法 SQL
        AirlineOrder update = new AirlineOrder();
        update.setStatus(AirlineOrder.STATUS_CANCELLED);
        int updated = orderMapper.update(update, Wrappers.<AirlineOrder>lambdaUpdate()
                .eq(AirlineOrder::getOrderNo, orderNo)
                .eq(AirlineOrder::getStatus, AirlineOrder.STATUS_NORMAL));
        pendingCancels.remove(conversationId);
        if (updated == 0) {
            return "退订失败：订单 " + orderNo + " 不存在或已是已退订状态。";
        }

        log.info("订单退订成功: {}（会话 {}）", orderNo, conversationId);
        return "退订成功：订单 " + orderNo + " 已由正常变更为已退订。";
    }

    /**
     * 从查询快照位置起，扫描对话记忆中 USER 消息是否出现确认词。
     * 同一轮内查询后立即退订时，本轮对话尚未由记忆 Advisor 写入，扫描不到确认 → 被拒。
     */
    private boolean hasCustomerConfirmation(String conversationId, int fromIndex) {
        List<Message> history = chatMemory.get(conversationId);
        if (history == null || history.size() <= fromIndex) {
            return false;
        }
        return history.subList(fromIndex, history.size()).stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .anyMatch(message -> CONFIRM_KEYWORDS.stream().anyMatch(message.getText()::contains));
    }

    private String conversationIdOf(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object value = toolContext.getContext().get(CONVERSATION_ID_KEY);
        return value == null ? null : value.toString();
    }

    private int memorySize(String conversationId) {
        List<Message> history = chatMemory.get(conversationId);
        return history == null ? 0 : history.size();
    }

    /** 测试辅助：清空退订门禁状态 */
    public void resetPendingCancels() {
        pendingCancels.clear();
    }

    private record PendingCancel(String orderNo, int memorySnapshot) {
    }

}
