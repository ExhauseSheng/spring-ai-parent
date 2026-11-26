-- Chat memory table (widened conversation_id, same as quick-start)
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`)
);

-- Airline orders. status uses codes: NORMAL / CANCELLED (mapped to Chinese in Java,
-- so no non-ASCII literals in SQL scripts and no charset ambiguity).
CREATE TABLE IF NOT EXISTS airline_order (
    order_no   VARCHAR(32)  NOT NULL PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    departure  VARCHAR(50)  NOT NULL,
    destination VARCHAR(50) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL'
);
