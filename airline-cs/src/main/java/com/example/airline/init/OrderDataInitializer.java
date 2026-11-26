package com.example.airline.init;

import com.example.airline.domain.AirlineOrder;
import com.example.airline.mapper.AirlineOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 订单种子数据（幂等：已存在则跳过）。
 * <p>
 * 中文数据放在 Java 源码里（编译期 UTF-8）而不是 data.sql——
 * SQL 脚本按平台默认编码读取，Windows 下中文会乱码（见 quick-start 的 .properties 编码问题同源）。
 */
@Component
public class OrderDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderDataInitializer.class);

    private final AirlineOrderMapper orderMapper;

    public OrderDataInitializer(AirlineOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void run(String... args) {
        seed("FL001", "张三", "北京", "上海", AirlineOrder.STATUS_NORMAL);
        seed("FL002", "李四", "广州", "成都", AirlineOrder.STATUS_NORMAL);
        seed("FL003", "王五", "上海", "东京", AirlineOrder.STATUS_CANCELLED);
    }

    private void seed(String orderNo, String name, String departure, String destination, String status) {
        if (orderMapper.selectById(orderNo) != null) {
            return;
        }
        AirlineOrder order = new AirlineOrder();
        order.setOrderNo(orderNo);
        order.setName(name);
        order.setDeparture(departure);
        order.setDestination(destination);
        order.setStatus(status);
        orderMapper.insert(order);
        log.info("初始化订单种子数据: {}", order);
    }

}
