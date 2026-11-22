package com.example.springai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置：窗口式 {@link ChatMemory}（内存存储，保留最近 N 条消息）。
 * <p>
 * 各对话服务通过 {@code MessageChatMemoryAdvisor} 共享此 bean，
 * 按 {@code conversationId} 隔离不同会话（见 ChatMemory.CONVERSATION_ID）。
 */
@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(@Value("${app.chat.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

}
