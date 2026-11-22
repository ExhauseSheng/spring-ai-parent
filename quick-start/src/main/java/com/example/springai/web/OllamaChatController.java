package com.example.springai.web;

import com.example.springai.service.OllamaChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ollama 本地模型对话接口：POST /api/ollama/chat
 */
@RestController
@RequestMapping("/api/ollama")
public class OllamaChatController {

    private final OllamaChatService ollamaChatService;

    public OllamaChatController(OllamaChatService ollamaChatService) {
        this.ollamaChatService = ollamaChatService;
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request) {
        return new ChatReply(ollamaChatService.chat(request.message()));
    }

    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

}
