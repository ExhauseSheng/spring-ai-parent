package com.example.airline.web;

import com.example.airline.service.AirlineChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 航空客服对话接口。conversationId 可选：不传则服务端生成并返回，前端续用同一 ID 延续多轮记忆。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AirlineChatService airlineChatService;

    public ChatController(AirlineChatService airlineChatService) {
        this.airlineChatService = airlineChatService;
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        return new ChatReply(airlineChatService.chat(request.message(), conversationId), conversationId);
    }

    public record ChatRequest(String message, String conversationId) {
    }

    public record ChatReply(String reply, String conversationId) {
    }

}
