package com.example.airline;

import com.example.airline.domain.AirlineOrder;
import com.example.airline.mapper.AirlineOrderMapper;
import com.example.airline.tools.FlightOrderTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退订三重门禁测试（直接调用工具，不经过大模型，结果确定）：
 * <pre>
 * 门禁1 未查询 → 拒绝；门禁2 未获客户确认 → 拒绝；门禁3 查询+确认后 → 退订成功
 * </pre>
 * 需要 MySQL（airline_order 表由 schema.sql 创建，种子数据由 OrderDataInitializer 写入）。
 */
@SpringBootTest
class FlightOrderToolsGateTest {

    @Autowired
    private FlightOrderTools flightOrderTools;

    @Autowired
    private AirlineOrderMapper orderMapper;

    @Autowired
    private ChatMemory chatMemory;

    private String conversationId;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        // 每个用例用独立会话（门禁状态按会话隔离），并把 FL001 复位为正常
        conversationId = "gate-" + UUID.randomUUID();
        toolContext = new ToolContext(Map.of(FlightOrderTools.CONVERSATION_ID_KEY, conversationId));
        flightOrderTools.resetPendingCancels();

        AirlineOrder reset = new AirlineOrder();
        reset.setStatus(AirlineOrder.STATUS_NORMAL);
        orderMapper.update(reset, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AirlineOrder>lambdaUpdate()
                .eq(AirlineOrder::getOrderNo, "FL001"));
    }

    @Test
    void cancelWithoutQueryIsRejected() {
        String result = flightOrderTools.cancelFlightOrder("FL001", toolContext);

        System.out.println("【门禁1·未查询】" + result);
        assertThat(result).contains("退订被拒绝").contains("queryFlightOrder");
        assertThat(statusOf("FL001")).isEqualTo(AirlineOrder.STATUS_NORMAL);
    }

    @Test
    void cancelAfterQueryButWithoutConfirmationIsRejected() {
        flightOrderTools.queryFlightOrder("FL001", toolContext);
        String result = flightOrderTools.cancelFlightOrder("FL001", toolContext);

        System.out.println("【门禁2·未确认】" + result);
        assertThat(result).contains("退订被拒绝").contains("明确确认");
        assertThat(statusOf("FL001")).isEqualTo(AirlineOrder.STATUS_NORMAL);
    }

    @Test
    void queryThenConfirmThenCancelSucceeds() {
        String queryResult = flightOrderTools.queryFlightOrder("FL001", toolContext);
        System.out.println("【查询】\n" + queryResult);
        assertThat(queryResult).contains("张三").contains("北京").contains("正常");

        // 模拟下一轮：记忆 Advisor 已把客户"确认"写入对话记忆
        chatMemory.add(conversationId, List.of(new UserMessage("确认退订")));

        String cancelResult = flightOrderTools.cancelFlightOrder("FL001", toolContext);
        System.out.println("【门禁3·确认后退订】" + cancelResult);

        assertThat(cancelResult).contains("退订成功");
        assertThat(statusOf("FL001")).isEqualTo(AirlineOrder.STATUS_CANCELLED);
    }

    private String statusOf(String orderNo) {
        return orderMapper.selectById(orderNo).getStatus();
    }

}
