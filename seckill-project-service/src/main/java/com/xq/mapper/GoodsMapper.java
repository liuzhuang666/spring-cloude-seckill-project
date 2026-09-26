package com.xq.mapper;

import com.xq.domain.Goods;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GoodsMapper {
    //查询参与秒杀的商品信息
    List<Goods> selectSeckillGoods();

    //更新库存
    int updateStock(@Param("goodsId") Integer goodsId);

    Goods selectByPrimaryKey(Integer goodsId);

    int updateByPrimaryKey(Goods record);
}