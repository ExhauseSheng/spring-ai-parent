package com.example.springai.web;

import com.example.springai.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        return new ChatReply(chatService.chat(request.message()));
    }

    /**
     * 流式对话（SSE）：curl -N -X POST http://localhost:8080/api/chat/stream -H "Content-Type: application/json" -d '{"message":"你好"}'
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request.message());
    }

    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

}
