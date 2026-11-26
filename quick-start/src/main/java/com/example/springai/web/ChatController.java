package com.example.springai.web;

import com.example.springai.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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
     * 带本地工具的对话：模型可调用 LocalTools 中注册的 @Tool 方法（查天气、查VIP等），
     * 执行结果回传模型后生成最终回复。
     * curl -X POST http://localhost:8080/api/chat/tools -H "Content-Type: application/json" -d '{"message":"查一下U10086的VIP等级和北京天气"}'
     */
    @PostMapping("/tools")
    public ChatReply chatWithTools(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.conversationId());
        return new ChatReply(chatService.chatWithTools(request.message(), conversationId), conversationId);
    }

    /**
     * 按权限动态下发工具的对话（需 HTTP Basic 认证，权限映射存 MySQL）：
     * <pre>
     * curl -u alice:password -X POST http://localhost:8080/api/chat/tools/dynamic \
     *   -H "Content-Type: application/json" -d '{"message":"查U10086的VIP等级和北京天气"}'
     * </pre>
     * alice（ROLE_WEATHER）只能用天气工具，bob（ROLE_VIP）只能用 VIP 工具。
     */
    @PostMapping("/tools/dynamic")
    public ChatReply chatWithDynamicTools(@RequestBody ChatRequest request, Authentication authentication) {
        String conversationId = resolveConversationId(request.conversationId());
        String username = authentication.getName();
        return new ChatReply(
                chatService.chatWithPermissionTools(request.message(), conversationId, username),
                conversationId);
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
