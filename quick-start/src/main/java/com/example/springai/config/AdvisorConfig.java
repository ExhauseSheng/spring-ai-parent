package com.example.springai.config;

import com.example.springai.advisor.SensitiveWordFilterAdvisor;
import com.example.springai.advisor.TimingAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 公共 Advisor 装配：日志、耗时统计、敏感词（SafeGuard 拦输入 + 打码过滤输出）、多轮记忆。
 * <p>
 * 各对话服务注入 {@code List<Advisor>}（Spring 按 Ordered 自动排序）并挂到自己的 ChatClient：
 * <pre>
 * before: 记忆(-2147482648) → 日志(0) → 耗时(10) → SafeGuard拦输入(15) → 打码(20) → 模型
 * after : 打码 → 耗时 → 日志 → 记忆   （逆序执行，日志与记忆看到的是打码后的最终回复）
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class AdvisorConfig {

    /** 内置日志 Advisor：打印请求与响应（DEBUG 级别，见 logging.level 配置） */
    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor(0);
    }

    /** 自定义：调用耗时统计 */
    @Bean
    public TimingAdvisor timingAdvisor() {
        return new TimingAdvisor();
    }

    /** 内置：输入侧敏感词拦截——命中直接返回拒绝话术，不请求模型 */
    @Bean
    public SafeGuardAdvisor safeGuardAdvisor(@Value("${app.chat.sensitive-words:}") List<String> sensitiveWords) {
        return SafeGuardAdvisor.builder()
                .sensitiveWords(cleanWords(sensitiveWords))
                .failureResponse("消息包含敏感词，已被拦截，请修改后重试。")
                .order(15)
                .build();
    }

    /** 自定义：输出侧敏感词打码（命中替换为 ***，对话不中断） */
    @Bean
    public SensitiveWordFilterAdvisor sensitiveWordFilterAdvisor(
            @Value("${app.chat.sensitive-words:}") List<String> sensitiveWords) {
        return new SensitiveWordFilterAdvisor(cleanWords(sensitiveWords));
    }

    /** 多轮对话记忆（基于共享 ChatMemory bean） */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    private static List<String> cleanWords(List<String> words) {
        return words == null
                ? List.of()
                : words.stream().map(String::trim).filter(w -> !w.isEmpty()).toList();
    }

}
