package com.example.springai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatClient Advisor 功能测试（捕获型 ChatModel，不发真实请求）：
 * <ol>
 *   <li>SimpleLoggerAdvisor：内置日志增强，打印请求与响应</li>
 *   <li>自定义 CallAdvisor：请求侧改写 Prompt + 响应侧改写 ChatResponse</li>
 *   <li>MessageChatMemoryAdvisor + MessageWindowChatMemory：多轮对话记忆</li>
 * </ol>
 */
class ChatClientAdvisorTest {

    /** 捕获 ChatClient 实际组装的 Prompt；reply 可改写以测试响应侧过滤 */
    static class CapturingChatModel implements ChatModel {
        Prompt captured;
        String reply = "ok";

        @Override
        public ChatResponse call(Prompt prompt) {
            this.captured = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    private String allText(CapturingChatModel model) {
        assertThat(model.captured).as("模型应被调用").isNotNull();
        StringBuilder sb = new StringBuilder();
        for (Message m : model.captured.getInstructions()) {
            sb.append(m.getMessageType()).append(":").append(m.getText()).append("\n");
        }
        return sb.toString();
    }

    /** 方式1：SimpleLoggerAdvisor —— 内置请求/响应日志 */
    @Test
    void simpleLoggerAdvisorLogsRequestAndResponse() {
        Logger logger = (Logger) LoggerFactory.getLogger(SimpleLoggerAdvisor.class);
        // SimpleLoggerAdvisor 使用 debug 级别输出，需临时调低日志级别才能捕获
        var oldLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var model = new CapturingChatModel();
            ChatClient.builder(model)
                    .defaultAdvisors(new SimpleLoggerAdvisor())
                    .build()
                    .prompt().user("你好，测试日志").call().chatResponse();

            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            System.out.println("【SimpleLoggerAdvisor 日志】\n" + String.join("\n", messages));

            assertThat(messages)
                    .as("应打印 request 日志且包含用户消息")
                    .anyMatch(m -> m.startsWith("request:") && m.contains("你好，测试日志"));
            assertThat(messages)
                    .as("应打印 response 日志且包含模型回复")
                    .anyMatch(m -> m.startsWith("response:") && m.contains("ok"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }
    }

    /** 方式2：自定义 CallAdvisor —— 请求侧给 user 消息加标记，响应侧包装回复内容 */
    @Test
    void customAdvisorRewritesRequestAndResponse() {
        var model = new CapturingChatModel();

        CallAdvisor markerAdvisor = new CallAdvisor() {
            @Override
            public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
                // 请求侧：给最后一条 user 消息追加标记
                List<Message> instructions = new ArrayList<>(request.prompt().getInstructions());
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    if (instructions.get(i) instanceof UserMessage) {
                        instructions.set(i, new UserMessage(instructions.get(i).getText() + "（来自Advisor）"));
                        break;
                    }
                }
                ChatClientRequest modifiedRequest = new ChatClientRequest(
                        new Prompt(instructions, request.prompt().getOptions()), request.context());

                // 传递链，拿到模型响应
                ChatClientResponse response = chain.nextCall(modifiedRequest);

                // 响应侧：包装回复内容
                String text = response.chatResponse().getResults().get(0).getOutput().getText();
                ChatResponse wrapped = new ChatResponse(
                        List.of(new Generation(new AssistantMessage("【已拦截】" + text))));
                return new ChatClientResponse(wrapped, response.context());
            }

            @Override
            public String getName() {
                return "marker-advisor";
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(markerAdvisor)
                .build();

        String content = client.prompt().user("原始问题").call().content();

        System.out.println("【自定义 Advisor】模型收到: " + allText(model).replace("\n", " | "));
        System.out.println("【自定义 Advisor】客户端收到: " + content);

        assertThat(allText(model))
                .as("请求侧改写应到达模型")
                .contains("原始问题（来自Advisor）");
        assertThat(content)
                .as("响应侧改写应到达客户端")
                .isEqualTo("【已拦截】ok");
    }

    /**
     * 方式4：实现 BaseAdvisor —— before/after 分离式钩子。
     * <p>
     * BaseAdvisor 继承 CallAdvisor + StreamAdvisor，adviseCall/adviseStream 已有默认实现
     * （自动串联 before → 链 → after），只需实现 before（请求侧）与 after（响应侧），
     * 同一份代码对 call() 和 stream() 都生效。MessageChatMemoryAdvisor 即基于它实现。
     */
    @Test
    void baseAdvisorBeforeAndAfterHooks() {
        var model = new CapturingChatModel();
        StringBuilder trace = new StringBuilder();

        BaseAdvisor baseAdvisor = new BaseAdvisor() {
            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                trace.append("before | ");
                List<Message> instructions = new ArrayList<>(request.prompt().getInstructions());
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    if (instructions.get(i) instanceof UserMessage) {
                        instructions.set(i, new UserMessage(instructions.get(i).getText() + "（BaseAdvisor改写）"));
                        break;
                    }
                }
                return new ChatClientRequest(
                        new Prompt(instructions, request.prompt().getOptions()), request.context());
            }

            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                trace.append("after | ");
                String text = response.chatResponse().getResults().get(0).getOutput().getText();
                ChatResponse wrapped = new ChatResponse(
                        List.of(new Generation(new AssistantMessage("【BaseAdvisor】" + text))));
                return new ChatClientResponse(wrapped, response.context());
            }

            @Override
            public int getOrder() {
                return 0;
            }

            @Override
            public String getName() {
                // BaseAdvisor 默认返回 getClass().getSimpleName()，匿名内部类为空字符串会报错
                return "base-advisor";
            }
        };

        String content = ChatClient.builder(model)
                .defaultAdvisors(baseAdvisor)
                .build()
                .prompt().user("基础钩子问题").call().content();

        System.out.println("【BaseAdvisor】执行轨迹: " + trace);
        System.out.println("【BaseAdvisor】模型收到: " + allText(model).replace("\n", " | "));
        System.out.println("【BaseAdvisor】客户端收到: " + content);

        assertThat(trace.toString())
                .as("应按 before → 模型 → after 顺序执行")
                .isEqualTo("before | after | ");
        assertThat(allText(model))
                .as("before 钩子的改写应到达模型")
                .contains("基础钩子问题（BaseAdvisor改写）");
        assertThat(content)
                .as("after 钩子的改写应到达客户端")
                .isEqualTo("【BaseAdvisor】ok");
    }

    /** 内置 SafeGuardAdvisor：输入含敏感词时短路拦截，不请求模型，返回拒绝话术 */
    @Test
    void safeGuardAdvisorBlocksSensitiveInput() {
        var model = new CapturingChatModel();
        var guard = new org.springframework.ai.chat.client.advisor.SafeGuardAdvisor(
                List.of("傻瓜", "笨蛋"), "包含敏感词，已拦截", 15);

        String content = ChatClient.builder(model).defaultAdvisors(guard).build()
                .prompt().user("你是个傻瓜").call().content();

        System.out.println("【SafeGuard·输入拦截】客户端收到: " + content);
        assertThat(model.captured)
                .as("命中敏感词时不应调用模型")
                .isNull();
        assertThat(content)
                .isEqualTo("包含敏感词，已拦截");
    }

    /** 手写敏感词过滤 Advisor：输出侧打码（输入侧由 SafeGuardAdvisor 拦截） */
    @Test
    void sensitiveWordFilterAdvisorMasksOutput() {
        var advisor = new com.example.springai.advisor.SensitiveWordFilterAdvisor(List.of("傻瓜", "笨蛋"));

        var model = new CapturingChatModel();
        model.reply = "你这个傻瓜";
        String content = ChatClient.builder(model).defaultAdvisors(advisor).build()
                .prompt().user("骂我").call().content();

        System.out.println("【敏感词·输出打码】客户端收到: " + content);
        assertThat(content)
                .isEqualTo("你这个***");
    }

    /** 方式3：MessageChatMemoryAdvisor —— 多轮对话记忆，第二轮携带第一轮历史 */
    @Test
    void chatMemoryAdvisorCarriesConversationHistory() {
        var model = new CapturingChatModel();
        ChatMemory memory = MessageWindowChatMemory.builder().build();

        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        String conversationId = "demo-conv";

        // 第一轮：告知信息（conversationId 必须通过 advisor param 传入请求上下文）
        client.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user("我叫小明")
                .call().chatResponse();
        String firstRound = allText(model);
        System.out.println("【记忆·第一轮】模型收到: " + firstRound.replace("\n", " | "));

        // 第二轮：提问，应带上第一轮历史
        client.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user("我叫什么名字？")
                .call().chatResponse();
        String secondRound = allText(model);
        System.out.println("【记忆·第二轮】模型收到: " + secondRound.replace("\n", " | "));

        assertThat(secondRound)
                .as("第二轮 Prompt 应包含第一轮对话历史")
                .contains("我叫小明")
                .contains("我叫什么名字？");

        assertThat(memory.get(conversationId))
                .as("ChatMemory 应按会话 ID 存储对话")
                .isNotEmpty();
    }

}
