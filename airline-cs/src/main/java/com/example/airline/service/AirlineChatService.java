package com.example.airline.service;

import com.example.airline.advisor.OrderAnswerGuardAdvisor;
import com.example.airline.tools.FlightOrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 航空客服对话服务：DeepSeek + JDBC 对话记忆 + 订单 Tools。
 * <p>
 * 系统提示词规定退订流程（先查询展示 → 询问确认 → 客户确认后才可退订），
 * 代码层另有 FlightOrderTools 的三重门禁兜底。
 */
@Service
public class AirlineChatService {

    private static final String SYSTEM_PROMPT = """
            你是航空公司的智能客服，可以使用两个工具：queryFlightOrder（查询订单）和 cancelFlightOrder（退订订单）。
            退订流程必须严格遵守：
            1. 客户提出退订时，必须先调用 queryFlightOrder 查询订单，并把订单编码、姓名、出发地、目的地、状态完整展示给客户；
            2. 展示之后必须询问客户是否确认退订；
            3. 只有当客户明确回复确认（如"确认"、"同意"）之后，才可以调用 cancelFlightOrder；
            4. 未经查询、或客户尚未确认时，绝不调用 cancelFlightOrder；
            5. 客户只询问订单信息时，仅调用查询工具即可；
            6. 涉及任何订单的具体信息（姓名、出发地、目的地、状态）时，必须调用 queryFlightOrder 获取，
               严禁凭空编造或根据上下文猜测订单字段——即使上下文出现过其他订单的信息也不得套用。
            回复使用中文，简洁、礼貌、专业。""";

    private final ChatClient chatClient;

    private final FlightOrderTools flightOrderTools;

    public AirlineChatService(DeepSeekChatModel chatModel, ChatMemory chatMemory, FlightOrderTools flightOrderTools) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                // 顺序（数组即执行序，after 逆序）：记忆 → 日志 → 订单回答守卫
                // 守卫在最内层：其 after 最先执行，编造回复在进入日志/记忆之前就被替换
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor(0),
                        new OrderAnswerGuardAdvisor())
                .build();
        this.flightOrderTools = flightOrderTools;
    }

    /**
     * @param conversationId 会话 ID：既用于记忆（advisor 参数），也通过 toolContext 传给
     *                       工具做退订门禁（同一会话才能累积"已查询/已确认"状态）
     */
    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.of(FlightOrderTools.CONVERSATION_ID_KEY, conversationId))
                .user(message)
                .tools(flightOrderTools)
                .call()
                .content();
    }

}
