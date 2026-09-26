package com.xq.mapper;

import com.xq.domain.Order;

//@Mapper
public interface OrderMapper {
    //秒杀成功后，生成订单信息
    int insert(Order record);
}