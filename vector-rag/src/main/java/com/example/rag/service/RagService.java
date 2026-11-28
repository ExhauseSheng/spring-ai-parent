package com.example.rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 向量 RAG 服务框架：文档入库（Ollama qwen3-embedding → Milvus）+ 检索增强对话（DeepSeek）。
 *
 * <h3>链路（三条 Advisor 依次生效）</h3>
 * <pre>
 * ① MessageChatMemoryAdvisor  —— 注入历史对话（JDBC 持久化）
 * ② SimpleLoggerAdvisor        —— 打印完整请求/响应（DEBUG，含检索到的上下文）
 * ③ RetrievalAugmentationAdvisor —— before 阶段：用问题向量化后在 Milvus 相似检索 topK
 *                                    文档，拼进 prompt 上下文；模型据此作答（RAG）
 * </pre>
 * 检索只发生在请求当下，注入的上下文不会写回记忆（记忆只存用户问题与最终回答）。
 */
@Service
public class RagService {

    private static final int RETRIEVE_TOP_K = 3;

    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    public RagService(DeepSeekChatModel chatModel, ChatMemory chatMemory, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是企业知识库问答助手。请基于检索到的上下文回答问题；"
                        + "上下文中没有的信息，明确告知用户知识库中未收录，不要编造。")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor(0),
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .vectorStore(vectorStore)
                                        .topK(RETRIEVE_TOP_K)
                                        .build())
                                .build())
                .build();
    }

    /** 文档入库：文本 → Ollama 向量化 → 写入 Milvus */
    public int ingest(List<String> texts) {
        vectorStore.add(texts.stream().map(Document::new).toList());
        return texts.size();
    }

    /** 纯向量检索（不经过大模型，便于单独验证 Milvus + 向量模型） */
    public List<String> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build())
                .stream()
                .map(Document::getText)
                .toList();
    }

    /** 检索增强对话（多轮记忆 + RAG 上下文） */
    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

    public static String newConversationId() {
        return UUID.randomUUID().toString();
    }

}
