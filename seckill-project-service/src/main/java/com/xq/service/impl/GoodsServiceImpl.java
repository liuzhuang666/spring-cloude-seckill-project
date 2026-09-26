package com.xq.service.impl;

import com.xq.domain.Goods;
import com.xq.domain.Order;
import com.xq.mapper.GoodsMapper;
import com.xq.mapper.OrderMapper;
import com.xq.service.GoodsService;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;

public class GoodsServiceImpl implements GoodsService {
    @Resource
    GoodsMapper goodsMapper;

    @Resource
    OrderMapper orderMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void realSeckill(Integer userId, Integer goodsId) {
        int i = goodsMapper.updateStock(goodsId);
        //订单生成
        if(i > 0){
            Order order = new Order();
            order.setGoodsid(goodsId);
            order.setUserid(userId);
            order.setCreatetime(new Date());
            orderMapper.insert(order);
        }
    }
}
