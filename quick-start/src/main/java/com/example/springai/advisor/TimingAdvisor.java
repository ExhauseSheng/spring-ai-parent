package com.example.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/**
 * 自定义耗时统计 Advisor（基于 {@link BaseAdvisor}）：
 * before 记录起始时间（ThreadLocal，线程安全），after 计算并打印调用耗时。
 */
public class TimingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TimingAdvisor.class);

    private final ThreadLocal<Long> startNanos = new ThreadLocal<>();

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        startNanos.set(System.nanoTime());
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Long start = startNanos.get();
        startNanos.remove();
        if (start != null) {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("ChatClient 调用耗时: {} ms", millis);
        }
        return response;
    }

    @Override
    public String getName() {
        return "timing";
    }

    @Override
    public int getOrder() {
        return 10;
    }

}
