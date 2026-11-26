package com.example.airline;

import com.example.airline.advisor.OrderAnswerGuardAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单回答守卫单测：验证"未经查询的订单详情回复被拦截、真实查询后的回复放行"。
 */
class OrderAnswerGuardAdvisorTest {

    private final OrderAnswerGuardAdvisor guard = new OrderAnswerGuardAdvisor();

    private ChatClientRequest request() {
        return new ChatClientRequest(new Prompt(List.of(new UserMessage("FL003"))), Map.of());
    }

    /** 桩链路：直接返回给定文本的"模型回复" */
    private CallAdvisorChain chainReturning(String replyText) {
        ChatClientResponse fake = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(replyText)))),
                Map.of());
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                return fake;
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor advisor) {
                return this;
            }
        };
    }

    @Test
    void blocksFabricatedOrderDetailsWithoutQuery() {
        String fabricated = "为您查询到订单信息如下：\n- 订单编码：FL003\n- 姓名：李四\n- 出发地：广州";

        ChatClientResponse result = guard.adviseCall(request(), chainReturning(fabricated));

        String text = result.chatResponse().getResults().get(0).getOutput().getText();
        System.out.println("【未查询→拦截】" + text);
        assertThat(text)
                .as("未调用查询工具却输出订单详情，必须被拦截")
                .contains("已被安全拦截")
                .doesNotContain("李四");
    }

    @Test
    void passesReplyAfterRealQuery() {
        String real = "订单编码：FL003\n姓名：王五\n状态：已退订";
        ChatClientResponse fake = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(real)))),
                Map.of());

        // 模拟真实链路：查询工具在 nextCall（链路）执行期间被调用并打标
        ChatClientResponse result = guard.adviseCall(request(), new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                OrderAnswerGuardAdvisor.markQueried();
                return fake;
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor advisor) {
                return this;
            }
        });

        String text = result.chatResponse().getResults().get(0).getOutput().getText();
        System.out.println("【已查询→放行】" + text);
        assertThat(text).isEqualTo(real);
    }

    @Test
    void passesNonOrderReply() {
        String ask = "请问您要查询的订单编码是多少呢？";

        ChatClientResponse result = guard.adviseCall(request(), chainReturning(ask));

        String text = result.chatResponse().getResults().get(0).getOutput().getText();
        System.out.println("【非订单详情→放行】" + text);
        assertThat(text).isEqualTo(ask);
    }

}
