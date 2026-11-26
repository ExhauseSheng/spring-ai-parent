package com.example.springai.service;

import com.example.springai.tools.LocalTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * DeepSeek 对话服务：通过 Spring AI 的 {@link ChatClient} 抽象调用大模型。
 * <p>
 * 公共 Advisor（日志、耗时、敏感词过滤、多轮记忆）由 {@code AdvisorConfig} 统一装配，
 * 注入 {@code List<Advisor>} 按 Ordered 排序后挂到客户端；相同 conversationId 的调用携带历史。
 * <p>
 * 注入具体类型 {@link DeepSeekChatModel} 是因为本应用同时装配了多个 ChatModel
 * （DeepSeek、百炼 DashScope、Ollama），用接口注入会产生歧义。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    private final LocalTools localTools;

    private final ToolPermissionService toolPermissionService;

    public ChatService(DeepSeekChatModel chatModel, List<Advisor> advisors, LocalTools localTools,
                       ToolPermissionService toolPermissionService) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个乐于助人的中文助手。")
                .defaultAdvisors(advisors)
                .build();
        this.localTools = localTools;
        this.toolPermissionService = toolPermissionService;
    }

    /**
     * 单轮调用：每次生成独立会话 ID，不携带历史（兼容旧用法）。
     */
    public String chat(String message) {
        return chat(message, UUID.randomUUID().toString());
    }

    /**
     * 多轮调用：传入相同 conversationId 即可携带该会话的历史消息。
     */
    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

    /**
     * 带工具的对话：模型可发起 tool_call，在本地执行 {@link LocalTools} 的 @Tool 方法后
     * 把结果回传模型继续生成（闭环由 Spring AI 自动完成，原理见 LocalTools 类注释）。
     */
    public String chatWithTools(String message) {
        return chatWithTools(message, UUID.randomUUID().toString());
    }

    public String chatWithTools(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .tools(localTools)
                .call()
                .content();
    }

    /**
     * 按权限动态下发工具的对话，链路（原理见 ToolPermissionService 与 LocalTools 注释）：
     * <ol>
     *   <li>MySQL 查询该用户被授权的工具名（users→authorities→sys_role_tool→sys_tool）；</li>
     *   <li>{@code ToolCallbacks.from(localTools)} 生成全部 @Tool 回调，按授权名单过滤；</li>
     *   <li>{@code .toolCallbacks(过滤后列表)} —— 模型只"看得见"有权限的工具，
     *       未授权工具不出现在 prompt 中，模型无法调用（不是执行时才拦截，而是根本不声明）。</li>
     * </ol>
     */
    public String chatWithPermissionTools(String message, String conversationId, String username) {
        List<String> allowedToolNames = toolPermissionService.getAllowedToolNames(username);
        List<ToolCallback> allowedCallbacks = Arrays.stream(ToolCallbacks.from(localTools))
                .filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
                .toList();

        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .toolCallbacks(allowedCallbacks)
                .call()
                .content();
    }

    public Flux<String> chatStream(String message) {
        return chatStream(message, UUID.randomUUID().toString());
    }

    /**
     * 流式多轮调用：SSE 无法在响应体回传 ID，需客户端自带并复用 conversationId。
     */
    public Flux<String> chatStream(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }

}
