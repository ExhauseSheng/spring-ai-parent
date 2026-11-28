package com.example.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文装配测试：MilvusVectorStore、Ollama 向量模型、DeepSeek、JDBC 记忆等 bean 全部就绪。
 * 需要本地 Milvus(19530)、Ollama(11434)、MySQL 已启动。
 */
@SpringBootTest
class VectorRagContextTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void contextLoadsWithVectorStoreBean() {
        assertThat(vectorStore)
                .as("MilvusVectorStore 应已自动装配")
                .isNotNull();
        System.out.println("VectorStore bean: " + vectorStore.getClass().getName());
    }

}
