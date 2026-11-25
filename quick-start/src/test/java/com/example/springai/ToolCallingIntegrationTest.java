package com.example.springai;

import com.example.springai.service.ChatService;
import com.example.springai.tools.LocalTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大模型 Tool Calling 集成测试：模型发起 tool_call → 本地反射执行 @Tool 方法 → 结果回传 → 最终回复。
 * 原理见 {@link LocalTools} 类注释。需要 DeepSeek API Key（环境变量）。
 */
@SpringBootTest
class ToolCallingIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private LocalTools localTools;

    /**
     * 一次提问触发两个本地工具：模型无法凭空知道 VIP 等级与天气，
     * 只有真正执行了本地工具并把结果喂回模型，回复里才可能出现这些数据。
     */
    @Test
    void modelInvokesLocalTools() {
        localTools.resetCallTrail();

        String reply = chatService.chatWithTools("查询用户 U10086 的VIP等级，再查一下北京的天气");

        System.out.println("【本地工具执行轨迹】" + localTools.getCallTrail());
        System.out.println("【最终回复】" + reply);

        assertThat(localTools.getCallTrail())
                .as("两个本地 @Tool 方法都应被模型触发并在本进程执行")
                .anyMatch(t -> t.contains("queryVipLevel") && t.contains("U10086"))
                .anyMatch(t -> t.contains("getCurrentWeather") && t.contains("北京"));

        assertThat(reply)
                .as("回复应包含本地工具返回的数据（黄金VIP / 25°C）")
                .contains("黄金")
                .contains("25");
    }

}
