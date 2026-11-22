package com.example.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * DeepSeek 对话服务：通过 Spring AI 的 {@link ChatClient} 抽象调用大模型。
 * <p>
 * 公共 Advisor（日志、耗时、敏感词过滤、多轮记忆）由 {@code AdvisorConfig} 统一装配，
 * 注入 {@code List<Advisor>} 按 Ordered 排序后挂到客户端；相同 conversationId 的调用携带历史。
 * <p>
 * 注入具体类型 {@link DeepSeekChatModel} 是因为本应用同时装配了多个 ChatModel
 * （DeepSeek、百炼 DashScope、Ollama），用接口注入会产生歧义。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(DeepSeekChatModel chatModel, List<Advisor> advisors) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个乐于助人的中文助手。")
                .defaultAdvisors(advisors)
                .build();
    }

    /**
     * 单轮调用：每次生成独立会话 ID，不携带历史（兼容旧用法）。
     */
    public String chat(String message) {
        return chat(message, UUID.randomUUID().toString());
    }

    /**
     * 多轮调用：传入相同 conversationId 即可携带该会话的历史消息。
     */
    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

    public Flux<String> chatStream(String message) {
        return chatStream(message, UUID.randomUUID().toString());
    }

    /**
     * 流式多轮调用：SSE 无法在响应体回传 ID，需客户端自带并复用 conversationId。
     */
    public Flux<String> chatStream(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }

}
