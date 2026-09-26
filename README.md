# 秒杀系统（spring-cloude-seckill-project）

一个用于学习的**高并发秒杀系统**教学项目。核心目标是演示如何在并发场景下**防止库存超卖**、**防止用户重复下单**，并理解「Redis 前置拦截 + RocketMQ 异步削峰 + 数据库兜底」的经典秒杀架构。

> 说明：工程名带有 `spring-cloude`，但当前实现主要基于 **Spring Boot**，并未使用 Spring Cloud 组件，请以实际代码为准。

---

## 一、技术栈

| 技术 | 用途 | 版本 |
|------|------|------|
| Spring Boot | 应用基础框架 | 2.7.1 |
| Java | 运行环境 | 1.8 |
| Redis | 预扣减库存、去重、分布式锁 | — |
| RocketMQ | 异步削峰、消息投递 | rocketmq-spring-boot-starter 2.2.2 |
| MyBatis | 数据库访问 | mybatis-spring-boot-starter 2.3.0 |
| MySQL | 最终权威库存与订单存储 | mysql-connector-java 5.1.6 |
| Lombok | 简化实体类样板代码 | — |
| Fastjson | JSON 序列化 | web 端 1.2.76 / service 端 2.0.25 |

---

## 二、系统架构

项目采用 **Maven 多模块** 结构，由两个可独立启动的 Spring Boot 应用组成：

```
spring-cloude-seckill-project（父工程，packaging=pom）
├── seckill-project-web       # 秒杀入口（Web 端，端口 7001）
│     接收 HTTP 请求 → 去重 → Redis 预扣减 → 发送 MQ 消息
└── seckill-project-service   # 秒杀处理（Service 端，端口 8085）
      启动预热库存 → 监听 MQ → 分布式锁 → 数据库扣库存 → 生成订单
```

两个模块通过 **RocketMQ** 解耦：web 端只负责「快速拦截 + 发消息」，真正扣库存的慢操作交给 service 端异步消化，实现削峰填谷。

---

## 三、秒杀核心链路

```
① 启动预热（service 端 DataSync）
   MySQL 商品库存 ──@PostConstruct──► Redis（key: goodsId:{id}）

② 秒杀请求（web 端 SeckillController）
   去重      setIfAbsent("uk{userId}_{goodId}")   ── 防重复下单
   预扣减    decrement("goodsId:{goodId}")        ── 防超量进入
   └─ 通过 → 异步发送 MQ（topic: secKillTopic9）

③ 异步消费（service 端 SeckillListener）
   Redis 分布式锁 lock:{goodsId}                 ── 全局互斥
   数据库条件更新 where id = ? and stocks > 0    ── 最终兜底防超卖
   生成订单（order_records）
```

**核心思想**：Redis 快、原子、跨进程，扛住高并发的第一道闸门；数据库条件更新是最终权威兜底；去重与唯一索引保证幂等。三层各司其职，共同保证「不超卖、不重复」。

---

## 四、工程结构

```
spring-cloude-seckill-project
├── pom.xml                              # 父工程，统一依赖版本
├── .gitignore
├── README.md
├── 秒杀并发控制知识点.md                 # 配套学习文档（9 种并发控制方案详解）
├── seckill-project-web/
│   └── src/main/
│       ├── java/com/xq/
│       │   ├── SecKillWebApplication.java
│       │   ├── config/RedisConfig.java      # RedisTemplate 序列化配置
│       │   └── controller/SeckillController.java  # 秒杀接口
│       └── resources/application.yml
└── seckill-project-service/
    └── src/main/
        ├── java/com/xq/
        │   ├── SecKillServiceApplication.java
        │   ├── config/DataSync.java         # 启动时预热库存到 Redis
        │   ├── config/RedisConfig.java
        │   ├── domain/Goods.java            # 商品实体
        │   ├── domain/Order.java            # 订单实体
        │   ├── listener/SeckillListener.java# MQ 消费者（分布式锁 + 扣库存）
        │   ├── mapper/GoodsMapper.java
        │   ├── mapper/OrderMapper.java
        │   ├── service/GoodsService.java
        │   └── service/impl/GoodsServiceImpl.java
        └── resources/
            ├── application.yml
            └── mapper/GoodsMapper.xml、OrderMapper.xml
```

---

## 五、数据库表设计

> 项目未附带建表 SQL，以下结构根据 Mapper XML 推断，请据此自行建库建表（库名 `seckill`）。

**goods（商品表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 主键（实体中对应 goodsId） |
| goods_name | varchar | 商品名称 |
| price | decimal | 现价 |
| content | text | 详细描述 |
| status | int | 1 正常 / 0 下架 / -1 删除 |
| stocks | int | 总库存 |
| create_time | datetime | 录入时间 |
| update_time | datetime | 修改时间 |
| spike | int | 是否参与秒杀（1 是 / 0 否） |

**order_records（订单表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 主键，自增（useGeneratedKeys） |
| user_id | int | 用户 ID |
| goods_id | int | 商品 ID |
| create_time | datetime | 下单时间 |

> 建议：在 `order_records(user_id, goods_id)` 上建**联合唯一索引**，作为防重复下单的最终兜底。

---

## 六、环境要求

| 依赖 | 说明 |
|------|------|
| JDK | 1.8+ |
| Maven | 3.x |
| MySQL | 本地 `localhost:3306`，库名 `seckill` |
| Redis | 本地 `127.0.0.1:6379` |
| RocketMQ | NameServer 地址 `192.168.88.131:9876`（按需改成自己的） |

数据库账号密码、RocketMQ 地址等，见各模块 `application.yml`。

---

## 七、快速开始

1. **启动中间件**：确保 MySQL、Redis、RocketMQ（NameServer + Broker）已启动。

2. **初始化数据库**：创建 `seckill` 库及 `goods`、`order_records` 表，并插入一条参与秒杀的商品（`status=1, spike=1, stocks>0`）。

3. **启动 service 端**（先启动，完成库存预热 + 开始监听 MQ）：
   ```bash
   cd seckill-project-service
   mvn spring-boot:run
   # 或直接运行 SecKillServiceApplication，端口 8085
   ```

4. **启动 web 端**（接收秒杀请求）：
   ```bash
   cd seckill-project-web
   mvn spring-boot:run
   # 或直接运行 SecKillWebApplication，端口 7001
   ```

5. **发起秒杀请求**：
   ```
   GET http://localhost:7001/seckill?goodsId=3
   ```

---

## 八、接口说明

| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/seckill` | GET | `goodsId`（商品 ID） | 发起秒杀。userId 由 `AtomicInteger` 模拟自增生成 |

返回示例：
- 重复抢购 → `您已经参与过了商品抢购，请下次再来`
- 库存扣完 → `货物抢购已经完毕`
- 抢购成功 → `商品正在抢购中`（实际订单由 service 端异步生成）

---

## 九、并发控制设计要点

本项目防超卖采用**多级防护**，详见配套文档 [`秒杀并发控制知识点.md`](./秒杀并发控制知识点.md)：

| 层级 | 方案 | 作用 |
|------|------|------|
| 第一层 | Redis `SETNX` 去重 | 防同一用户重复下单 |
| 第二层 | Redis `DECR` 预扣减 | 前置拦截，挡掉超量请求 |
| 第三层 | Redis 分布式锁（SETNX + 过期 + 自旋 + Lua 释放） | 消费端全局互斥 |
| 第四层 | 数据库条件更新 `where stocks > 0` | 最终权威兜底，保证不超卖 |

**关键实现**：
- `GoodsServiceImpl.realSeckill`：用 `updateStock` 的影响行数判断扣减是否成功，成功才生成订单，全程 `@Transactional`。
- `SeckillListener`：分布式锁使用「UUID token + Lua 脚本」防止误删他人锁，超时自旋退出并抛异常让 MQ 重投。

---

## 十、学习建议

1. 先跑通链路，再用压测工具（JMeter 等）观察不同并发下的表现。
2. 对照 [`秒杀并发控制知识点.md`](./秒杀并发控制知识点.md) 理解「先查后改为什么会超卖」以及各方案的取舍。
3. 可以尝试移除某一层防护（如注释掉分布式锁或条件更新），对比超卖现象，加深理解。
