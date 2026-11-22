package com.example.springai;

import com.example.springai.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC（MySQL）对话记忆持久化测试：验证消息真实落库，而非仅存内存。
 * 需要本地 MySQL（3306，root/root，spring_ai 库）已启动。
 */
@SpringBootTest
class JdbcChatMemoryTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private JdbcChatMemoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void messagesPersistToMysql() {
        String conversationId = "jdbc-" + UUID.randomUUID();

        chatService.chat("我叫小刚，请记住我的名字", conversationId);

        // 1) 仓库层读取（直接查库）
        var messages = repository.findByConversationId(conversationId);
        System.out.println("JDBC 仓库读到消息数: " + messages.size());
        messages.forEach(m -> System.out.println("  " + m.getMessageType() + ": " + m.getText()));
        assertThat(messages)
                .as("消息应已写入 JDBC 仓库")
                .isNotEmpty();

        // 2) 全新 ChatMemory 实例（同一数据源）也能读到历史 —— 证明数据不在内存
        ChatMemory freshMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .build();
        assertThat(freshMemory.get(conversationId))
                .as("新实例从库中读到同一会话")
                .isNotEmpty();

        // 3) 原生 SQL 直查 MySQL 表，确认行数
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class, conversationId);
        System.out.println("SPRING_AI_CHAT_MEMORY 行数: " + count);
        assertThat(count)
                .as("MySQL 表中应有该会话的行")
                .isGreaterThan(0);
    }

}
