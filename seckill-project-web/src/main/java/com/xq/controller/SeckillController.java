package com.xq.controller;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class SeckillController {
    //原子操作
    AtomicInteger userIdAt= new AtomicInteger(0);
    @Resource
    RocketMQTemplate rocketMQTemplate;
    //
    @Resource
    RedisTemplate redisTemplate;

    @GetMapping("seckill")
    public String doSeckill(Integer goodId) {
        //模拟生成用户的id 在项目中我们一般使用用户上下文对象去获取用户id，在这里我们使用AtomicInteger模拟获取用户id
        int userId = userIdAt.incrementAndGet(); //i++原子操作
        //货物的去重操作
        String uk = userId + "_" + goodId;
        //flag true值设置成功，false值设置不成功
        boolean flag = redisTemplate.opsForValue().setIfAbsent("uk" + uk, "");
        if (!flag) {
            return "您已经参与过了商品抢购，请下次再来";
        }

        //执行库存的预扣减的操作
        Long count  = redisTemplate.opsForValue().decrement("goodsId:"+goodId);
        if(count<=0){
            redisTemplate.opsForValue().set("goodsId:"+goodId,0);//保证最小库存是零
            return "货物抢购已经完毕";
        }

        //异步操作，发送给mq
        rocketMQTemplate.asyncSend("secKillTopic9", uk, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                System.out.println("消息发送成功");
            }

            @Override
            public void onException(Throwable throwable) {
                System.out.println("消息发送失败"+throwable.getMessage());
                System.out.println("用户ID" + userId + "商品Id" + goodId);
            }
        });
        return "商品正在抢购中";
    }
}
// http://localhost:7001/seckill?goodsId=3

