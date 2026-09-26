package com.xq.config;

import com.xq.domain.Goods;
import com.xq.mapper.GoodsMapper;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

@Component
public class DataSync {
    @Resource
    private GoodsMapper goodsMapper;
    @Resource
    RedisTemplate redisTemplate;

    //数据同步方法，要service服务启动时就启动initData方法
    @PostConstruct
    public void initData() {
        List<Goods> goodsList = goodsMapper.selectSeckillGoods();   //获取了mysql中的所用商品信息
        if(CollectionUtils.isEmpty(goodsList)) {
            return;
        }
        goodsList.forEach(goods -> {
            redisTemplate.opsForValue().set("goodsId:" + goods.getGoodsId(),goods.getTotalStocks());
        });//存入redis中

    }
}
