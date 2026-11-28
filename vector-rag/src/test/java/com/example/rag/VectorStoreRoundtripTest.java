package com.example.rag;

import com.example.rag.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量链路回环测试（不经过大模型，零 LLM 成本）：
 * 文本 → Ollama(qwen3-embedding:0.6b) 向量化 → 写入 Milvus → 相似检索命中。
 * 证明：向量模型与向量库的配置、维度(1024)、bean 装配全部正确。
 */
@SpringBootTest
class VectorStoreRoundtripTest {

    private static final String MARKER = "phoenix-milvus-roundtrip-777";

    @Autowired
    private RagService ragService;

    @Test
    void embedAddAndSearchRoundtrip() {
        String content = "连通性测试文档：项目代号 Phoenix-777，关键词 " + MARKER + "，用于验证 Ollama 与 Milvus 链路。";

        ragService.ingest(List.of(content));

        List<String> results = ragService.search(MARKER, 3);

        System.out.println("检索命中 " + results.size() + " 条，首条: "
                + (results.isEmpty() ? "(空)" : results.get(0).substring(0, Math.min(60, results.get(0).length()))));

        assertThat(results)
                .as("入库后按相同关键词应能检索到该文档")
                .isNotEmpty()
                .anyMatch(text -> text.contains(MARKER));
    }

}
