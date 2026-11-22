package com.example.springai;

import com.example.springai.service.OllamaChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ollama 本地模型（qwen3:8b）对话集成测试。
 * <p>
 * 无需 API Key，但需要本地 Ollama 服务已启动且已拉取 qwen3:8b 模型：
 * <pre>
 * ./mvnw -pl quick-start test -Dtest=OllamaChatIntegrationTest
 * </pre>
 */
@SpringBootTest
class OllamaChatIntegrationTest {

    @Autowired
    private OllamaChatService ollamaChatService;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Test
    void chatReturnsReply() {
        String reply = ollamaChatService.chat("用一句话介绍你自己");

        System.out.println("Ollama 回复: " + reply);

        assertThat(reply)
                .as("Ollama 应返回非空回复")
                .isNotBlank();
    }

    /**
     * 开启深度思考：qwen3 会先在内部推理（Ollama 的 message.thinking 字段，Spring AI 不透出），
     * 表现为 completionTokens 显著升高，最终 content 是干净的结论。
     * 实测本题：开启约 650+ tokens，关闭约 220 tokens。
     */
    @Test
    void thinkingEnabledConsumesMoreTokens() {
        ChatResponse response = ollamaChatModel.call(new Prompt(
                "9.11 和 9.9 哪个大？先仔细思考，再给出答案。",
                OllamaChatOptions.builder().model("qwen3:8b").enableThinking().build()));

        AssistantMessage output = response.getResults().get(0).getOutput();
        int completionTokens = response.getMetadata().getUsage().getCompletionTokens();

        System.out.println("【思考开启】completionTokens=" + completionTokens);
        System.out.println("【思考开启】content: " + output.getText());

        assertThat(output.getText())
                .as("开启思考后应返回最终答案")
                .isNotBlank();
        assertThat(completionTokens)
                .as("开启思考应消耗大量 token（思考过程），实测约 650，阈值 400")
                .isGreaterThan(400);
    }

    /**
     * 关闭深度思考：模型把推理直接写进 content，completionTokens 明显低于开启状态。
     */
    @Test
    void thinkingDisabledAnswersWithFewerTokens() {
        ChatResponse response = ollamaChatModel.call(new Prompt(
                "9.11 和 9.9 哪个大？先仔细思考，再给出答案。",
                OllamaChatOptions.builder().model("qwen3:8b").disableThinking().build()));

        AssistantMessage output = response.getResults().get(0).getOutput();
        int completionTokens = response.getMetadata().getUsage().getCompletionTokens();

        System.out.println("【思考关闭】completionTokens=" + completionTokens);
        System.out.println("【思考关闭】content: " + output.getText());

        assertThat(output.getText())
                .as("关闭思考后应直接返回答案")
                .isNotBlank();
        assertThat(completionTokens)
                .as("关闭思考无内部推理，token 应明显更少，实测约 220，阈值 400")
                .isLessThan(400);
        assertThat(output.getText())
                .as("关闭思考时推理过程会直接出现在 content 中")
                .contains("9.11");
    }

}
