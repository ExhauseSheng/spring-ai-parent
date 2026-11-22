-- 基于 Spring AI 自带 schema-mysql.sql 修改：
-- conversation_id 由 VARCHAR(36) 加宽为 VARCHAR(255)——
-- 36 仅够纯 UUID，而本项目会话 ID 带前缀（如 mem-<uuid>），且 API 允许客户端自定义 conversationId。
-- 通过 spring.ai.chat.memory.repository.jdbc.schema 指向本文件（启动时 initialize-schema: always 幂等执行）
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,

    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`)
);
