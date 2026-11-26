package com.example.airline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airline.domain.AirlineOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 航空订单 Mapper（MyBatis-Plus BaseMapper：单查/列表/条件更新均无需手写 SQL）。
 */
@Mapper
public interface AirlineOrderMapper extends BaseMapper<AirlineOrder> {
}
