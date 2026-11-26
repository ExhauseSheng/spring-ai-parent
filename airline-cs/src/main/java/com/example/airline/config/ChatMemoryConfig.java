package com.example.airline.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆：窗口式 ChatMemory + JDBC 存储（MySQL SPRING_AI_CHAT_MEMORY 表，重启不丢）。
 * 表由本模块 schema.sql 创建（JdbcChatMemoryRepository 的自动建表默认 embedded 模式，对 MySQL 不生效）。
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
