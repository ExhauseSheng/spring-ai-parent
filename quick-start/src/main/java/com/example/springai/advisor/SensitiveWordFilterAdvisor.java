package com.example.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤 Advisor（自定义，基于 {@link BaseAdvisor}）—— 输出侧打码：
 * after 阶段把模型回复中的敏感词替换为 ***（命中不中断对话）。
 * <p>
 * 输入侧由内置 {@code SafeGuardAdvisor}（order 15，本类之前）拦截：
 * 含敏感词的输入直接拒答，不会到达本类与模型。
 * <p>
 * 敏感词列表通过 {@code app.chat.sensitive-words} 配置（逗号分隔）。
 */
public class SensitiveWordFilterAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilterAdvisor.class);

    private static final String MASK = "***";

    private final List<String> sensitiveWords;

    public SensitiveWordFilterAdvisor(List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords == null
                ? List.of()
                : sensitiveWords.stream().map(String::trim).filter(w -> !w.isEmpty()).toList();
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 输入侧不在此过滤：SafeGuardAdvisor（order 更小）已先一步拦截含敏感词的输入
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (sensitiveWords.isEmpty()) {
            return response;
        }
        List<Generation> generations = response.chatResponse().getResults();
        List<Generation> filtered = new ArrayList<>(generations.size());
        boolean changed = false;
        for (Generation generation : generations) {
            String text = generation.getOutput().getText();
            String masked = filter(text);
            if (masked.equals(text)) {
                filtered.add(generation);
            } else {
                filtered.add(new Generation(new AssistantMessage(masked)));
                changed = true;
            }
        }
        if (!changed) {
            return response;
        }
        log.info("已过滤模型回复中的敏感词");
        return new ChatClientResponse(new ChatResponse(filtered), response.context());
    }

    private String filter(String text) {
        String result = text;
        for (String word : sensitiveWords) {
            result = result.replace(word, MASK);
        }
        return result;
    }

    @Override
    public String getName() {
        return "sensitive-word-filter";
    }

    @Override
    public int getOrder() {
        return 20;
    }

}
