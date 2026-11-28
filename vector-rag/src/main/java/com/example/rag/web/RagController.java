package com.example.rag.web;

import com.example.rag.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 向量 RAG 查询接口（文档录入已拆分到 {@link KnowledgeDocumentController}）：
 * <ul>
 *   <li>POST /api/rag/search —— 纯向量检索（不走大模型，单独验证向量链路）</li>
 *   <li>POST /api/rag/chat   —— 检索增强对话（多轮记忆 + RAG）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/search")
    public SearchReply search(@RequestBody SearchRequest2 request) {
        int topK = request.topK() == null || request.topK() <= 0 ? 3 : request.topK();
        return new SearchReply(ragService.search(request.query(), topK));
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request) {
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        return new ChatReply(ragService.chat(request.message(), conversationId), conversationId);
    }

    public record SearchRequest2(String query, Integer topK) {
    }

    public record SearchReply(List<String> contents) {
    }

    public record ChatRequest(String message, String conversationId) {
    }

    public record ChatReply(String reply, String conversationId) {
    }

}
