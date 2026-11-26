package com.example.airline.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 订单回答守卫 Advisor：拦截"未经查询就输出订单详情"的编造回复。
 * <p>
 * 背景问题：大模型（temperature 较高时）可能跳过 queryFlightOrder 工具、
 * 凭空编造订单字段（实测发生过：问 FL003 却编出 FL002 的数据），
 * 且编造结果会被对话记忆存档、污染后续轮次。
 * <p>
 * 机制（around式，代码层强制，不依赖模型自觉）：
 * <pre>
 * before：ThreadLocal 标志置 false → 执行链路（模型可能调用查询工具，工具会 markQueried）
 * after ：若回复包含"订单编码："（订单详情格式）但本轮【从未调用查询工具】，
 *         则替换为拦截提示——且发生在记忆 Advisor 保存之前，编造内容不会进入历史。
 * </pre>
 * 标志用 ThreadLocal：call() 路径下工具与 Advisor 在同一线程执行。
 */
public class OrderAnswerGuardAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(OrderAnswerGuardAdvisor.class);

    /** 本轮是否调用过查询工具（工具在 queryFlightOrder 成功后置位） */
    private static final ThreadLocal<Boolean> QUERY_CALLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 订单详情格式特征：'订单编码：FL001' / '订单编码: FL001' */
    private static final Pattern ORDER_DETAIL_PATTERN = Pattern.compile("订单编码[：:]");

    /** 供 {@code FlightOrderTools.queryFlightOrder} 标记"本轮已真实查询" */
    public static void markQueried() {
        QUERY_CALLED.set(Boolean.TRUE);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        QUERY_CALLED.set(Boolean.FALSE);
        ChatClientResponse response = chain.nextCall(request);
        boolean queried = Boolean.TRUE.equals(QUERY_CALLED.get());
        QUERY_CALLED.remove();

        if (queried) {
            return response;
        }

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResults().isEmpty()) {
            return response;
        }
        String text = chatResponse.getResults().get(0).getOutput().getText();
        if (text != null && ORDER_DETAIL_PATTERN.matcher(text).find()) {
            log.error("拦截到未经查询工具的订单详情回复（疑似编造），已替换: {}",
                    text.substring(0, Math.min(80, text.length())).replace('\n', ' '));
            ChatResponse blocked = new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "系统检测到本条回复包含未经查询的订单信息，已被安全拦截。"
                            + "请让我先用查询工具查询该订单，向您展示真实信息后再继续。"))));
            return new ChatClientResponse(blocked, response.context());
        }
        return response;
    }

    @Override
    public String getName() {
        return "order-answer-guard";
    }

    @Override
    public int getOrder() {
        // 记忆(-2147482648) → 日志(0) → 守卫(5)：after 阶段守卫最先执行，替换后日志与记忆看到的都是拦截结果
        return 5;
    }

}
