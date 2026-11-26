package com.example.airline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 航空订单实体（MyBatis-Plus 映射 airline_order 表）。
 * <p>
 * status 在库中存代码 NORMAL / CANCELLED，展示层通过 {@link #getStatusText()} 映射为
 * 中文"正常 / 已退订"——SQL 脚本保持纯 ASCII，中文一律放在 Java 侧（编译期 UTF-8，无编码风险）。
 */
@TableName("airline_order")
public class AirlineOrder {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 订单编码（业务主键，由种子数据/调用方指定，故用 INPUT） */
    @TableId(value = "order_no", type = IdType.INPUT)
    private String orderNo;

    /** 姓名 */
    private String name;

    /** 出发地 */
    private String departure;

    /** 目的地 */
    private String destination;

    /** 订单状态：NORMAL / CANCELLED */
    private String status;

    public AirlineOrder() {
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** 状态展示文案：正常 / 已退订 */
    public String getStatusText() {
        return STATUS_CANCELLED.equals(status) ? "已退订" : "正常";
    }

    @Override
    public String toString() {
        return "AirlineOrder{orderNo='" + orderNo + "', name='" + name + "', departure='" + departure
                + "', destination='" + destination + "', status='" + status + "'}";
    }

}
