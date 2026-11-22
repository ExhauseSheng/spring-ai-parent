package com.example.springai.web;

import com.example.springai.service.BailianChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 阿里云百炼（Qwen）对话接口：POST /api/bailian/chat
 * conversationId 可选：不传则服务端生成并返回；传同一 ID 延续多轮对话。
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
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        return new ChatReply(bailianChatService.chat(request.message(), conversationId), conversationId);
    }

    public record ChatRequest(String message, String conversationId) {
    }

    public record ChatReply(String reply, String conversationId) {
    }

}
