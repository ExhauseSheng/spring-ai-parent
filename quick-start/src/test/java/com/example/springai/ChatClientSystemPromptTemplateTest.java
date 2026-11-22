package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatClient 系统提示词的使用方式与传参测试。
 * <p>
 * 关键结论（实测）：
 * <ul>
 *   <li>占位符语法：单花括号 {@code {var}}（默认 StTemplateRenderer，分隔符为 {'{'} 和 {'}'）；
 *       {@code {{var}}}、{@code <var>}、{@code #{var}} 均不是有效语法</li>
 *   <li>传参：{@code param(k, v)} 单个 / {@code params(map)} 批量</li>
 *   <li>Builder 的 defaultSystem 是兜底，每次调用的 system() 优先</li>
 *   <li>{@code .call()} 只返回 CallResponseSpec，需再调 {@code .content()} / {@code .chatResponse()} 才真正执行</li>
 * </ul>
 * 用捕获型 ChatModel 验证 Prompt 组装，不发真实请求。
 */
class ChatClientSystemPromptTemplateTest {

    /** 捕获 ChatClient 实际组装的 Prompt */
    static class CapturingChatModel implements ChatModel {
        Prompt captured;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.captured = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
    }

    private String systemText(CapturingChatModel model) {
        assertThat(model.captured).as("ChatClient 应已调用模型").isNotNull();
        return model.captured.getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .map(Message::getText)
                .findFirst()
                .orElse("(无 system 消息)");
    }

    /** 方式1：Builder.defaultSystem 静态文本（客户端级默认，无模板） */
    @Test
    void defaultSystemStaticText() {
        var model = new CapturingChatModel();
        ChatClient.builder(model).defaultSystem("你是一个乐于助人的助手。").build()
                .prompt().user("你好").call().chatResponse();

        assertThat(systemText(model)).isEqualTo("你是一个乐于助人的助手。");
        assertThat(model.captured.getInstructions().stream()
                .anyMatch(m -> m.getMessageType() == MessageType.USER)).isTrue();
    }

    /** 方式2：每次调用的 system() 优先于 defaultSystem（defaultSystem 只是兜底） */
    @Test
    void perCallSystemOverridesDefaultSystem() {
        var model = new CapturingChatModel();
        ChatClient.builder(model).defaultSystem("默认提示词").build()
                .prompt().system("本次提示词").user("你好").call().chatResponse();

        assertThat(systemText(model))
                .as("调用级 system() 应覆盖 defaultSystem")
                .isEqualTo("本次提示词");
    }

    /** 方式3：system(text 模板) + param(k, v) 传参，占位符为单花括号 {var} */
    @Test
    void systemTemplateWithParam() {
        var model = new CapturingChatModel();
        ChatClient.create(model).prompt()
                .system(s -> s.text("你是{style}助手，说话要像{style}。").param("style", "诗人"))
                .user("介绍一下西湖")
                .call().chatResponse();

        String text = systemText(model);
        assertThat(text)
                .as("占位符 {style} 应被 param 值替换")
                .isEqualTo("你是诗人助手，说话要像诗人。");
    }

    /** 方式4：params(Map) 批量传参 */
    @Test
    void systemTemplateWithParamsMap() {
        var model = new CapturingChatModel();
        ChatClient.create(model).prompt()
                .system(s -> s.text("你的名字是{name}，来自{city}。")
                        .params(Map.of("name", "小明", "city", "杭州")))
                .user("你好")
                .call().chatResponse();

        assertThat(systemText(model))
                .isEqualTo("你的名字是小明，来自杭州。");
    }

    /** 方式5：system(Resource) 从模板文件加载 + param 传参 */
    @Test
    void systemTemplateFromResource() {
        var model = new CapturingChatModel();
        ChatClient.create(model).prompt()
                .system(s -> s.text(new ClassPathResource("prompt/system-template.txt"))
                        .param("role", "翻译官"))
                .user("你好")
                .call().chatResponse();

        String text = systemText(model);
        assertThat(text)
                .as("Resource 模板中的 {role} 应被替换")
                .contains("单括号:翻译官");
        assertThat(text)
                .as("非语法占位符（{{}}、<>、#{}）不会被替换")
                .contains("尖括号:<role>")
                .contains("SpEL:#翻译官");
    }

}
