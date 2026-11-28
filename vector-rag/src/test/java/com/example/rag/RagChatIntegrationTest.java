package com.example.rag;

import com.example.rag.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 全链路集成测试：入库私有知识 → RetrievalAugmentationAdvisor 检索 → DeepSeek 基于上下文作答。
 * 问题答案只存在于知识库里（模型不可能凭记忆知道），因此命中即证明"向量检索确实进入了 ChatClient 上下文"。
 * 需要 DEEPSEEK_API_KEY、Milvus、Ollama、MySQL。
 */
@SpringBootTest
class RagChatIntegrationTest {

    @Autowired
    private RagService ragService;

    @Test
    void retrievalAugmentedAnswerUsesIngestedKnowledge() {
        // 语义检索需要一点语料，先补两条背景文档（幂等：重复入库不影响命中）
        ragService.ingest(List.of(
                "内部知识库：项目代号 Phoenix-777 的应急机房位于银川，于2026年投入运行。",
                "内部知识库：Phoenix-777 项目由基础设施组负责，负责人是林岚。"));

        String answer = ragService.chat("项目代号 Phoenix-777 的应急机房位于哪里？", RagService.newConversationId());

        System.out.println("【RAG 回答】" + answer);

        assertThat(answer)
                .as("回答应包含仅存在于知识库中的'银川'（证明检索上下文生效）")
                .contains("银川");
    }

}
