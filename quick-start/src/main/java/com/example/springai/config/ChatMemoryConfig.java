package com.example.springai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置：窗口式 {@link ChatMemory}，存储层为 {@link JdbcChatMemoryRepository}，
 * 持久化到本地 MySQL（spring_ai 库的 SPRING_AI_CHAT_MEMORY 表，重启不丢）。
 * <p>
 * JdbcChatMemoryRepository 由 {@code JdbcChatMemoryRepositoryAutoConfiguration} 自动装配
 * （依赖 application.yml 中的数据源配置与 initialize-schema: always 自动建表）。
 * 各对话服务通过 {@code MessageChatMemoryAdvisor} 共享此 bean，按 conversationId 隔离会话。
 */
@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcRepository,
                                 @Value("${app.chat.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcRepository)
                .maxMessages(maxMessages)
                .build();
    }

}
