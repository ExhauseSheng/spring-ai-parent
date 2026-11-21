package com.example.springai;

import com.example.springai.service.BailianChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阿里云百炼（Qwen）对话功能集成测试：会发起真实的模型调用（消耗少量 API 额度）。
 * <p>
 * 需要环境变量 {@code DASHSCOPE_API_KEY}（百炼控制台创建）；未设置时测试自动跳过。
 * 运行：
 * <pre>
 * DASHSCOPE_API_KEY=sk-xxx ./mvnw -pl quick-start test -Dtest=BailianChatIntegrationTest
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class BailianChatIntegrationTest {

    @Autowired
    private BailianChatService bailianChatService;

    @Test
    void chatReturnsReply() {
        String reply = bailianChatService.chat("用一句话介绍你自己");

        System.out.println("百炼回复: " + reply);

        assertThat(reply)
                .as("百炼应返回非空回复")
                .isNotBlank();
    }

}
