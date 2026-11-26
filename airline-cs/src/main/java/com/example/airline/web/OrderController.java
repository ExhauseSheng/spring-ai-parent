package com.example.airline.web;

import com.example.airline.domain.AirlineOrder;
import com.example.airline.mapper.AirlineOrderMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单列表接口（供前端表格展示，退订后前端可刷新看到状态变化）。
 * status 在响应中直接给出中文文案（正常 / 已退订）。
 */
@RestController
public class OrderController {

    private final AirlineOrderMapper orderMapper;

    public OrderController(AirlineOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @GetMapping("/api/orders")
    public List<OrderView> list() {
        return orderMapper.selectList(null).stream()
                .map(order -> new OrderView(
                        order.getOrderNo(), order.getName(),
                        order.getDeparture(), order.getDestination(),
                        order.getStatusText()))
                .toList();
    }

    public record OrderView(String orderNo, String name, String departure, String destination, String status) {
    }

}
