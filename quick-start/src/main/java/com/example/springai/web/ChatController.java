package com.example.springai.web;

import com.example.springai.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * DeepSeek 对话接口。
 * <p>
 * conversationId 可选：不传则服务端生成并在响应中返回；后续传同一 ID 即可延续多轮对话。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.conversationId());
        return new ChatReply(chatService.chat(request.message(), conversationId), conversationId);
    }

    /**
     * 流式对话（SSE）：curl -N -X POST http://localhost:8080/api/chat/stream -H "Content-Type: application/json" -d '{"message":"你好","conversationId":"my-conv-1"}'
     * SSE 无法回传 ID，客户端需自带并复用 conversationId 以延续多轮记忆。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request.message(), resolveConversationId(request.conversationId()));
    }

    private static String resolveConversationId(String conversationId) {
        return (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString()
                : conversationId;
    }

    public record ChatRequest(String message, String conversationId) {
    }

    public record ChatReply(String reply, String conversationId) {
    }

}
