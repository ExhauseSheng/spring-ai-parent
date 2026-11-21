package com.example.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * DeepSeek 对话服务：通过 Spring AI 的 {@link ChatClient} 抽象调用大模型。
 * 注入具体类型 {@link DeepSeekChatModel} 是因为本应用同时装配了多个 ChatModel
 * （DeepSeek、百炼 DashScope），用接口注入会产生歧义。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(DeepSeekChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个乐于助人的中文助手。")
                .build();
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * 流式对话：模型逐段返回内容（SSE），前端可实时展示。
     */
    public Flux<String> chatStream(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

}
