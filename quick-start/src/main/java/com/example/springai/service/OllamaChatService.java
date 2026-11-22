package com.example.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Ollama 本地模型对话服务（qwen3:8b）。
 * 公共 Advisor（日志、耗时、敏感词过滤、多轮记忆）由 {@code AdvisorConfig} 统一装配。
 */
@Service
public class OllamaChatService {

    private final ChatClient chatClient;

    public OllamaChatService(OllamaChatModel chatModel, List<Advisor> advisors) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个乐于助人的中文助手。")
                .defaultAdvisors(advisors)
                .build();
    }

    /** 单轮调用：每次生成独立会话 ID，不携带历史 */
    public String chat(String message) {
        return chat(message, UUID.randomUUID().toString());
    }

    /** 多轮调用：传入相同 conversationId 即可携带历史 */
    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

}
