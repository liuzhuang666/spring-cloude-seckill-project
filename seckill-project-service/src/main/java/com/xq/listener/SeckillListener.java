package com.xq.listener;


import com.xq.service.GoodsService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

//监听器，用来监听web服务发来的消息
@Component
@RocketMQMessageListener(topic = "secKillTopic9",
consumerGroup = "seckill-consumer-group9",
consumeMode = ConsumeMode.CONCURRENTLY,//并发消费模式
consumeThreadNumber = 2)
public class SeckillListener implements RocketMQListener<MessageExt> {

    /** 抢锁的最大等待时间（毫秒），超过则放弃本次处理 */
    private static final int MAX_TIME = 20000;
    /** 锁的过期时间（秒），防止线程崩溃后锁永远不释放 */
    private static final long LOCK_EXPIRE_SECONDS = 30;
    /** 释放锁的 Lua 脚本：只有 value 匹配（锁是自己加的）才删除，保证原子性 */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) else return 0 end";

    @Resource
    GoodsService goodsService;

    @Resource
    RedisTemplate redisTemplate;

    @Override
    public void onMessage(MessageExt messageExt) {
        // 解析消息：userId_goodsId
        String message = new String(messageExt.getBody(), StandardCharsets.UTF_8);
        Integer userId = Integer.parseInt(message.split("_")[0]);
        Integer goodsId = Integer.parseInt(message.split("_")[1]);

        // 每个线程一个唯一 token，用于标识「这把锁是我加的」
        String token = UUID.randomUUID().toString();
        String lockKey = "lock:" + goodsId;

        int waited = 0;  // 已等待的毫秒数
        while (waited < MAX_TIME) {
            // 抢锁：SET lock:goodsId token NX PX 30000
            Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));

            if (Boolean.TRUE.equals(flag)) {  // 拿到锁
                try {
                    // 执行业务处理
                    goodsService.realSeckill(userId, goodsId);
                    return;  // 业务完成立即返回，避免死循环
                } finally {
                    // 释放锁：只有 token 匹配才删，防止误删别人的锁
                    redisTemplate.execute(
                            new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                            Collections.singletonList(lockKey),
                            token);
                }
            } else {  // 没拿到锁，自旋等待
                waited += 200;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();  // 恢复中断标志后退出
                    return;
                }
            }
        }

        // 等待超时仍未拿到锁：抛异常，让 RocketMQ 稍后重新投递这条消息
        throw new RuntimeException("获取锁超时: " + lockKey);
    }
}
