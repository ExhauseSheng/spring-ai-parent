package com.example.springai;

import com.example.springai.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeek 对话功能集成测试：会发起真实的模型调用（消耗少量 API 额度）。
 * <p>
 * 需要环境变量 {@code DEEPSEEK_API_KEY}；未设置时上下文无法启动，测试会失败。
 * 运行：
 * <pre>
 * DEEPSEEK_API_KEY=sk-xxx ./mvnw -pl quick-start test -Dtest=DeepSeekChatIntegrationTest
 * </pre>
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-key")
class DeepSeekChatIntegrationTest {

    @Autowired
    private ChatService chatService;

    /** 注入具体类型：应用中同时存在 dashScopeChatModel 与 deepSeekChatModel 两个 ChatModel bean */
    @Autowired
    private DeepSeekChatModel chatModel;

    @Test
    void chatReturnsReply() {
        String reply = chatService.chat("用一句话介绍你自己");

        System.out.println("DeepSeek 回复: " + reply);

        assertThat(reply)
                .as("DeepSeek 应返回非空回复")
                .isNotBlank();
    }

    @Test
    void chatStreamReturnsReplyInChunks() {
        List<String> chunks = chatService.chatStream("用一句话介绍你自己")
                .doOnNext(chunk -> System.out.println("流式片段: " + chunk))
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(chunks)
                .as("流式响应至少应有一个片段")
                .isNotEmpty();

        String reply = String.join("", chunks);
        System.out.println("流式拼接结果(共 " + chunks.size() + " 个片段): " + reply);

        assertThat(reply)
                .as("拼接后的完整回复应非空")
                .isNotBlank();
    }

    /**
     * 深度思考（deepseek-reasoner）：模型先输出思考过程（reasoning_content），再给出最终答案。
     * 与默认的 deepseek-chat 不同，reasoner 模型会返回两段内容。
     */
    @Test
    void reasonerProvidesThinkingBeforeAnswer() {
        ChatResponse response = ChatClient.create(chatModel)
                .prompt()
                .user("9.11 和 9.9 哪个大？先仔细思考，再给出答案。")
                .options(DeepSeekChatOptions.builder().model("deepseek-reasoner").build())
                .call()
                .chatResponse();

        AssistantMessage output = response.getResults().get(0).getOutput();

        assertThat(output)
                .as("reasoner 模型应返回 DeepSeekAssistantMessage")
                .isInstanceOf(DeepSeekAssistantMessage.class);

        String reasoning = ((DeepSeekAssistantMessage) output).getReasoningContent();
        String reply = output.getText();

        System.out.println("【思考过程 reasoning】\n" + reasoning);
        System.out.println("【最终回答 content】\n" + reply);

        assertThat(reasoning)
                .as("deepseek-reasoner 应返回思考过程")
                .isNotBlank();
        assertThat(reply)
                .as("应返回最终答案")
                .isNotBlank();
    }

}
