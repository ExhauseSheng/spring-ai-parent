package com.example.airline;

import com.example.airline.domain.AirlineOrder;
import com.example.airline.mapper.AirlineOrderMapper;
import com.example.airline.service.AirlineChatService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 航空客服 E2E 测试（真实 DeepSeek 调用，两轮对话走完整退订流程）：
 * <pre>
 * 第1轮 "我要退订FL001" → 模型调用查询工具，把订单信息展示给客户并询问确认
 * 第2轮 "确认"         → 模型调用退订工具，库里状态 NORMAL → CANCELLED
 * </pre>
 * 需要 DEEPSEEK_API_KEY 与 MySQL。
 */
@SpringBootTest
class AirlineChatFlowIntegrationTest {

    @Autowired
    private AirlineChatService airlineChatService;

    @Autowired
    private AirlineOrderMapper orderMapper;

    private String conversationId;

    @BeforeEach
    void setUp() {
        conversationId = "e2e-" + UUID.randomUUID();
        // 复位测试订单（上次运行可能已退订）
        AirlineOrder reset = new AirlineOrder();
        reset.setStatus(AirlineOrder.STATUS_NORMAL);
        orderMapper.update(reset, Wrappers.<AirlineOrder>lambdaUpdate()
                .eq(AirlineOrder::getOrderNo, "FL001"));
    }

    @Test
    void fullRefundFlowWithCustomerConfirmation() {
        // 第1轮：提出退订 → 应先查询并展示订单信息
        String firstReply = airlineChatService.chat("我要退订订单FL001", conversationId);
        System.out.println("【第1轮·模型回复】\n" + firstReply);

        assertThat(firstReply)
                .as("第1轮必须把查询到的订单信息展示给客户")
                .contains("FL001")
                .contains("张三");
        assertThat(orderMapper.selectById("FL001").getStatus())
                .as("第1轮不应执行退订（客户还没确认）")
                .isEqualTo(AirlineOrder.STATUS_NORMAL);

        // 第2轮：客户确认 → 应执行退订
        String secondReply = airlineChatService.chat("确认，退订吧", conversationId);
        System.out.println("【第2轮·模型回复】\n" + secondReply);

        assertThat(secondReply)
                .as("第2轮应退订成功（模型可能改述为'成功退订'等）")
                .containsAnyOf("退订成功", "成功退订");
        assertThat(orderMapper.selectById("FL001").getStatus())
                .as("数据库状态应已变更为已退订")
                .isEqualTo(AirlineOrder.STATUS_CANCELLED);
    }

}
