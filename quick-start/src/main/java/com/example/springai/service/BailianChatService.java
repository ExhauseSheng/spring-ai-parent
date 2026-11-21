package com.example.springai.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 阿里云百炼（DashScope）对话服务：调用 Qwen 系列模型。
 * 与 {@link ChatService}（DeepSeek）并行存在，各自注入具体的 ChatModel 实现类。
 */
@Service
public class BailianChatService {

    private final ChatClient chatClient;

    public BailianChatService(DashScopeChatModel chatModel) {
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
