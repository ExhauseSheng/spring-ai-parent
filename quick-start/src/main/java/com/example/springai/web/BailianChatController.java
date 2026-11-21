package com.example.springai.web;

import com.example.springai.service.BailianChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阿里云百炼（Qwen）对话接口：POST /api/bailian/chat
 */
@RestController
@RequestMapping("/api/bailian")
public class BailianChatController {

    private final BailianChatService bailianChatService;

    public BailianChatController(BailianChatService bailianChatService) {
        this.bailianChatService = bailianChatService;
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request) {
        return new ChatReply(bailianChatService.chat(request.message()));
    }

    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

}
