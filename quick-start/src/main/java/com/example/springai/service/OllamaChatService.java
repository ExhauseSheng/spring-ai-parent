package com.example.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

/**
 * Ollama 本地模型对话服务（qwen3:8b）。
 * 与 {@link ChatService}（DeepSeek）、{@link BailianChatService}（百炼）并行存在，
 * 各自注入具体的 ChatModel 实现类以避免 bean 注入歧义。
 */
@Service
public class OllamaChatService {

    private final ChatClient chatClient;

    public OllamaChatService(OllamaChatModel chatModel) {
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

}
