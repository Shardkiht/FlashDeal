---

<div align="center">

# ⚡ FlashDeal

### 高并发秒杀系统 | High-Concurrency Flash Sale System

基于 **Spring Boot 3 + Redis + RocketMQ** 构建的高可用、防超卖、防重复下单的秒杀系统

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-4.9.7-blue.svg)](https://rocketmq.apache.org/)
[![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-3.5.16-brightgreen.svg)](https://baomidou.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📖 项目简介

**FlashDeal** 是一个面向高并发场景的秒杀系统，核心解决电商促销、限量抢购等业务中常见的三大难题：**超卖**、**重复下单**、*
*流量洪峰**。系统通过 Redis Lua 脚本实现原子性库存预扣减，借助 RocketMQ 完成订单异步落库，并使用 Redisson 限流器保障系统稳定性。

整体设计遵循"**前置拦截 → 原子预扣 → 异步落库 → 失败回滚**"
的分层防御理念，单机可支撑每秒数千次秒杀请求，在保证业务正确性的同时最大化吞吐能力。项目代码结构清晰、注释完善，既可作为生产级秒杀方案的参考实现，也适合作为学习高并发架构设计的实战案例。

---

## 📊 压测数据

### 最新压测结果（5000用户 / 100连接 / 4线程 / 30s）

```
========================================
总请求: 844,121 | QPS: 28,055
--- 业务分类 ---
限流拦截: 753,718 (89.29%)  ← Redisson 全局限流器拦截
风控拦截:  86,036 (10.19%)  ← 决策树模型识别可疑请求
库存不足:   4,079 (0.48%)   ← Lua 脚本原子预扣拒绝
重复下单:     188 (0.02%)   ← Lua sadd 判重拦截
成功/处理中:  100 (0.01%)   ← 最终订单创建成功数
延迟: avg=4.98ms p50=1.95ms p99=18.48ms
========================================
```

### 关键验证结论

| 验证项      | 预期            | 实际                | 状态 |
|:---------|:--------------|:------------------|:---|
| **超卖防护** | 0 超卖          | ✅ 0 超卖            | 通过 |
| **重复下单** | 0 重复          | ✅ 188次被拦截         | 通过 |
| **库存精准** | 100库存→100订单   | ✅ 100 订单成功        | 通过 |
| **限流效果** | 3000 req/s 放行 | ✅ 约 3000 req/s 放行 | 通过 |
| **风控拦截** | 拦截可疑请求       | ✅ 10.19% 被拦截     | 通过 |
| **响应时间** | p99 < 50ms    | ✅ p99 = 18.48ms   | 优秀 |

### 性能分析

- **QPS ~28,055**：wrk 压测工具实测，4 线程 100 连接并发
- **四层防御生效**：限流层 89.29% → 风控层 10.19% → Lua 层 0.48% → DB 层兜底
- **平均延迟 4.98ms**：Redis 预扣 + MQ 异步发送，性能优异
- **风控模块**：决策树模型识别羊毛党与模拟器，拦截 10.19% 可疑请求

---

## ✨ 核心特性

| 特性             | 实现方式                              | 说明                                                 |
|:---------------|:----------------------------------|:---------------------------------------------------|
| 🚀 **流量整形**    | Redisson `RRateLimiter`           | 全局限流 3000 req/s，超出直接返回 HTTP 429    |
| 🔐 **登录鉴权**    | JWT + 拦截器                         | 无状态认证，Token 有效期 2 小时，从 `authentication` header 提取  |
| ⚡ **原子预扣**     | Redis Lua 脚本                      | `sadd` 判重 + `decr` 扣减原子完成，避免竞态                     |
| 🆔 **全局唯一 ID** | Hutool Snowflake                  | 41 位时间戳 + 10 位机器 ID + 12 位序列号，趋势递增，支持分布式           |
| 🔥 **库存预热**    | `@PostConstruct` 自动加载             | 应用启动时自动将 DB 秒杀库存同步到 Redis （仅 dev 环境）               |
| 📨 **异步落库**    | RocketMQ 异步发送                     | Redis 预扣成功后异步发送 MQ；ServiceImpl 统一捕获异常并回滚           |
| **DB 乐观锁**     | `WHERE stock > 0`                 | 数据库层兜底，防止超卖与重复下单                                   |
| 🔁 **三态幂等**    | Redis `PROCESSING/SUCCESS/FAILED` | 消费端三态幂等键，支持 MQ 重试与前端状态轮询                           |
| 💥 **超卖防御**    | DB 乐观锁 `stock > 0`                | `UPDATE ... SET stock = stock - 1 WHERE stock > 0` |
| 🔄 **失败回滚**    | Redis Lua 原子回滚 + FAIL 标记          | MQ 发送失败 / 消费业务异常时原子回滚库存并标记 FAILED，用户可立即重试          |
| 🔍 **定时对账**    | `@Scheduled` + Redis SCAN         | 每 5 分钟扫描 PROCESSING 卡单，对比 DB 修复不一致数据，兜底 MQ 重试耗尽场景  |

---

## 🛠️ 技术栈

| 分类         | 技术              | 版本          |
|:-----------|:----------------|:------------|
| **基础框架**   | Spring Boot     | 3.5.16      |
| **开发语言**   | Java            | 17          |
| **ORM 框架** | MyBatis Plus    | 3.5.16      |
| **关系型数据库** | MySQL           | 8.0+        |
| **缓存中间件**  | Redis           | 7.0+        |
| **限流**     | Redisson        | 3.27.0      |
| **消息队列**   | Apache RocketMQ | 4.9.7       |
| **认证授权**   | JWT             | 0.12.6      |
| **工具库**    | Hutool / Lombok | 5.8.34 / -- |

---

## 🏗️ 系统架构

下图展示了 FlashDeal 的整体架构与各组件协作关系。客户端请求经过限流器与登录拦截器后进入业务层，业务层通过 Redis 完成原子预扣，再通过
RocketMQ 异步落库，最终由消费者在幂等校验与 DB 乐观锁保护下写入 MySQL。

```mermaid
flowchart TB
    subgraph Client["客户端"]
        U["用户 App / Web"]
    end

    subgraph Gateway["接入层"]
        RL["Redisson 限流器<br/>3000 req/s"]
        LI["JWT 登录拦截器"]
    end

    subgraph App["应用层 Spring Boot"]
        CTRL["SeckillController"]
        SVC["SeckillServiceImpl"]
        IDG["SnowflakeIdGenerate<br/>全局唯一 ID"]
        PROD["SeckillProducer"]
        CONS["SeckillConsumer"]
    end

    subgraph Cache["缓存层 Redis"]
        STOCK[("seckill:{id}:stock<br/>库存")]
        ORDER[("seckill:{id}:order<br/>已购用户 Set")]
        IDEM[("seckill:{voucherId}:userId:consumed<br/>三态幂等键")]

        LUA["Lua 脚本<br/>原子预扣"]
        RBLUA["rollback.lua<br/>原子回滚"]
    end

    subgraph MQ["消息队列"]
        TOPIC[["voucher-order-topic"]]
    end

    subgraph DB["持久层"]
        MYSQL[("MySQL<br/>seckill_voucher<br/>voucher_order<br/>user")]
    end

    U -->|HTTP 请求| RL
    RL -->|放行| LI
    RL -->|超限| REJ["拒绝: RATE_LIMIT"]
    LI -->|Token 校验通过| CTRL
    LI -->|无 Token/失效| UNAUTH["200: 用户未登录"]
    CTRL --> SVC
    SVC --> IDG

    SVC -->|执行| LUA
    LUA --> STOCK
    LUA --> ORDER
    SVC -->|预扣成功| PROD
    PROD -->|异步发送| TOPIC
    PROD -.->|发送失败| RBLUA
    RBLUA --> STOCK
    RBLUA --> ORDER
    TOPIC --> CONS
    CONS -->|终态判断| IDEM
    CONS -->|DB 操作| MYSQL
    MYSQL -->|stock=stock-1<br/>WHERE stock>0| MYSQL

    style RL fill:#e65100,color:#fff
    style LUA fill:#2e7d32,color:#fff
    style TOPIC fill:#1565c0,color:#fff
    style MYSQL fill:#c62828,color:#fff
    style REJ fill:#b71c1c,color:#fff
    style UNAUTH fill:#b71c1c,color:#fff
```

---

## ⚡ 秒杀核心流程

下图展示了从用户点击"立即抢购"到订单创建完成的完整链路，包含限流、鉴权、Lua 原子预扣、MQ
异步发送、失败回滚等关键环节。这是整个系统最核心的流程，所有的高并发设计都集中体现在这里。

```mermaid
flowchart TD
    START([用户发起秒杀请求]) --> LIMIT{限流器<br/>tryAcquire}
    LIMIT -->|被限流| R1[返回: RATE_LIMIT]
    LIMIT -->|放行| AUTH{JWT 校验}
    AUTH -->|未登录| R2[返回: 用户未登录]
    AUTH -->|已登录| GEN[生成全局唯一订单 ID<br/>Snowflake]

    GEN --> LUA[执行 Lua 脚本<br/>原子操作]
    LUA --> CHECK1{库存 > 0?}
    CHECK1 -->|否| R3[返回: 优惠券已卖完<br/>Lua 返回 1]
    CHECK1 -->|是| CHECK2{用户已下单?<br/>sadd 判重}
    CHECK2 -->|是| R4[返回: 不能重复下单<br/>Lua 返回 2]
    CHECK2 -->|否| MARK[标记 PROCESSING<br/>写入三态幂等键]
    MARK --> MSG["生成 MQ 消息<br/>{userId, voucherId}"]
    MSG --> MQ{异步发送 RocketMQ}
    MQ -->|发送成功| OK[返回: 正在抢购中<br/>前端轮询状态]
    MQ -->|发送失败| ROLLBACK[执行 rollback.lua<br/>回滚库存+资格, 标记 FAILED]
    ROLLBACK --> R5[返回: 抢购失败<br/>用户可立即重试]

    style START fill:#6a1b9a,color:#fff
    style OK fill:#2e7d32,color:#fff
    style R1 fill:#b71c1c,color:#fff
    style R2 fill:#b71c1c,color:#fff
    style R3 fill:#b71c1c,color:#fff
    style R4 fill:#b71c1c,color:#fff
    style R5 fill:#b71c1c,color:#fff
    style LUA fill:#f9a825,color:#212121
    style ROLLBACK fill:#e65100,color:#fff
```

---

## 📨 MQ 消费与容错流程

下图展示了 RocketMQ 消费者侧的处理逻辑。消费者收到订单消息后，先做幂等校验，再在 DB 乐观锁保护下完成 DB
落库。若发生业务异常（如库存不足、重复下单），会自动回滚 Redis 预扣的库存，保证 Redis 与 DB 数据最终一致。

```mermaid
flowchart TD
    MSG(["收到 MQ 订单消息<br/>{userId, voucherId}"]) --> STATUS{读取幂等键状态}
    STATUS -->|SUCCESS/FAILED| SKIP[已是终态, 跳过]
    STATUS -->|PROCESSING| CREATE[构建订单对象<br/>createSeckillOrder]
    STATUS -->|不存在| HANDLEFAIL[handleFail<br/>查库兜底]

    CREATE --> DBSAVE{DB 操作}
    DBSAVE -->|成功| MARK[标记 SUCCESS]
    MARK --> DONE([消费完成])

    DBSAVE -->|业务异常| CHECK{查 DB<br/>订单是否落库}
    CHECK -->|已落库| MARK2[标记 SUCCESS<br/>不回滚]
    MARK2 --> DONE
    CHECK -->|未落库| RBLUA2[执行 rollback.lua<br/>原子回滚库存+资格+标记FAILED]
    RBLUA2 --> DONE

    DBSAVE -->|系统异常| THROW1[抛出异常<br/>触发 MQ 重试]
    THROW1 --> RETRY{{MQ 自动重试<br/>最多 3 次}}
    RETRY -->|重试耗尽| DLQ[进入死信队列<br/>人工介入]

    style MSG fill:#6a1b9a,color:#fff
    style DONE fill:#2e7d32,color:#fff
    style SKIP fill:#f9a825,color:#212121
    style THROW1 fill:#b71c1c,color:#fff
    style DLQ fill:#b71c1c,color:#fff
    style RBLUA2 fill:#e65100,color:#fff
    style DBSAVE fill:#1565c0,color:#fff
```

---

## 📁 项目结构

```
FlashDeal                                          # 多模块 Maven 父项目
├── pom.xml                                          # 父 POM（依赖管理）
├── flashdeal-core/                                  # 秒杀核心模块
│   ├── scripts/                                     # 压测脚本与报告
│   │   ├── register_test_users.sh                   # 批量注册压测用户
│   │   ├── diversify_user_profiles.sh               # 用户画像区分（正常/羊毛党）
│   │   ├── wrk_seckill_risk_test.lua                # wrk 风控压测 Lua 脚本
│   │   ├── risk_tokens.txt                          # 压测用户 Token 文件
│   │   └── benchmark-results.txt                    # 压测结果报告
│   ├── src/main/java/com/flashdeal
│   │   ├── FlashDealApplication.java                # 启动类
│   │   ├── controller/                              # 控制层
│   │   │   ├── UserController.java                  # 用户登录
│   │   │   ├── SeckillController.java               # 秒杀下单+状态查询入口
│   │   │   └── TestController.java                  # 测试: 添加秒杀券
│   │   ├── service/                                 # 服务层
│   │   │   ├── api/                                 # 接口定义
│   │   │   │   ├── UserService.java
│   │   │   │   ├── SeckillVoucherService.java
│   │   │   │   └── SeckillService.java
│   │   │   └── impl/                                # 接口实现
│   │   │       ├── UserServiceImpl.java
│   │   │       ├── SeckillVoucherServiceImpl.java   # 添加秒杀券+同步库存到Redis
│   │   │       └── SeckillServiceImpl.java          # ⭐ 秒杀核心逻辑
│   │   ├── task/                                    # 定时任务
│   │   │   └── SeckillReconciliationTask.java       # ⭐ 定时对账（PROCESSING 卡单修复）
│   │   ├── rocketmq/                                # MQ 生产/消费
│   │   │   ├── SeckillProducer.java                 # 异步发送+失败回滚
│   │   │   ├── SeckillConsumer.java                 # 幂等消费+失败回滚
│   │   │   └── SeckillFailLog.java                  # 失败核查记录实体
│   │   ├── mapper/                                  # MyBatis Plus Mapper
│   │   ├── domain/                                  # 实体与 DTO/VO
│   │   │   ├── User.java
│   │   │   ├── SeckillVoucher.java
│   │   │   ├── SeckillOrder.java
│   │   │   ├── dto/
│   │   │   │   ├── SeckillOrderMessage.java         # ⭐ MQ 消息体（userId + voucherId）
│   │   │   │   └── UserLoginDTO.java
│   │   │   ├── Result.java                          # 统一返回结果
│   │   │   └── vo/UserLoginVO.java
│   │   └── common/                                  # 公共组件
│   │       ├── config/                              # 配置类
│   │       │   ├── RedisConfig.java
│   │       │   ├── RedissonConfig.java              # Redis 密码从配置读取
│   │       │   ├── MybatisPlusConfig.java
│   │       │   └── WebMvcConfig.java                # 拦截器+消息转换器
│   │       ├── constant/                            # 常量
│   │       │   ├── RedisKeyConstant.java
│   │       │   ├── MessageConstant.java
│   │       │   ├── SeckillConstant.java
│   │       │   ├── PathConstant.java
│   │       │   └── JwtClaimsConstant.java
│   │       ├── exception/                           # 异常处理
│   │       │   ├── BaseException.java
│   │       │   ├── BusinessException.java
│   │       │   ├── LoginFailedException.java
│   │       │   └── GlobalExceptionHandler.java
│   │       ├── interceptor/                         # 拦截器
│   │       │   ├── RateLimitInterceptor.java        # 限流拦截器（最先执行）
│   │       │   └── LoginInterceptor.java            # JWT 登录拦截器
│   │       ├── utils/                               # 工具类
│   │       │   ├── JwtUtil.java
│   │       │   ├── SnowflakeIdGenerate.java         # 雪花算法全局唯一 ID
│   │       │   ├── LuaScriptUtil.java               # Lua 脚本加载器
│   │       │   └── UserHolder.java                  # ThreadLocal 用户上下文
│   │       ├── properties/JwtProperties.java
│   │       └── json/JacksonObjectMapper.java
│   └── src/main/resources/
│       ├── application.yaml                         # 应用配置
│       ├── application-dev.yaml                     # 开发环境配置
│       ├── mapper/UserMapper.xml
│       └── lua/
│           ├── seckill.lua                          # ⭐ 秒杀预扣 Lua 脚本
│           └── rollback.lua                         # ⭐ 失败回滚 Lua 脚本
└── risk-guard/                                      # 风控模块
    ├── src/main/java/com/flashdeal/riskguard
    │   ├── api/                                     # 接口定义
    │   │   └── RiskGuardClient.java                 # 风控客户端接口
    │   ├── dto/                                     # 数据传输对象
    │   │   ├── RiskRequest.java                     # 风控请求
    │   │   └── RiskDecision.java                    # 风控决策结果
    │   ├── feature/                                 # 特征提取
    │   │   ├── FeatureExtractor.java                # 特征提取接口
    │   │   └── impl/
    │   │       └── SeckillFeatureExtractor.java     # ⭐ 秒杀特征提取（Redis Pipeline）
    │   ├── model/                                   # 风控模型
    │   │   ├── RiskModel.java                       # 模型接口
    │   │   └── impl/
    │   │       └── DecisionTreeModel.java           # 决策树模型（Smile）
    │   ├── impl/
    │   │   └── InProcessRiskGuardClient.java        # 进程内风控客户端实现
    │   ├── config/
    │   │   └── RiskGuardAutoConfiguration.java      # Spring 自动装配
    │   └── train/                                   # 离线训练
    │       ├── BehaviorSimulator.java               # 行为模拟器（生成训练数据）
    │       └── ModelTrainer.java                    # 模型训练器
    ├── src/main/resources/
    │   └── model.bin                                  # 训练好的决策树模型
    └── pom.xml
```

---

## 🚀 快速开始

### 环境准备

| 依赖       | 最低版本  | 说明   |
|:---------|:------|:-----|
| JDK      | 17    | 必须   |
| Maven    | 3.8+  | 构建工具 |
| MySQL    | 8.0+  | 数据存储 |
| Redis    | 7.0+  | 缓存   |
| RocketMQ | 4.9.7 | 消息队列 |

### 1. 初始化数据库

```sql
-- 用户表
CREATE TABLE `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `openid`      VARCHAR(45)  DEFAULT NULL,
    `name`        VARCHAR(32)  DEFAULT NULL,
    `phone`       VARCHAR(11)  DEFAULT NULL,
    `sex`         VARCHAR(2)   DEFAULT NULL,
    `id_number`   VARCHAR(18)  DEFAULT NULL,
    `avatar`      VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 秒杀优惠券表
CREATE TABLE `seckill_voucher`
(
    `id`            BIGINT UNSIGNED DEFAULT NULL,
    `stock`         INT             DEFAULT NULL,
    `create_time`   TIMESTAMP       DEFAULT NULL,
    `begin_time`    TIMESTAMP       DEFAULT NULL,
    `end_time`      TIMESTAMP       DEFAULT NULL,
    `update_time`   TIMESTAMP       DEFAULT NULL,
    `initial_stock` INT             DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 优惠券订单表
CREATE TABLE `voucher_order`
(
    `id`          BIGINT           DEFAULT NULL,
    `user_id`     BIGINT UNSIGNED  DEFAULT NULL,
    `voucher_id`  BIGINT UNSIGNED  DEFAULT NULL,
    `pay_type`    TINYINT UNSIGNED DEFAULT NULL,
    `status`      TINYINT UNSIGNED DEFAULT NULL,
    `create_time` TIMESTAMP        DEFAULT NULL,
    `pay_time`    TIMESTAMP        DEFAULT NULL,
    `use_time`    TIMESTAMP        DEFAULT NULL,
    `refund_time` TIMESTAMP        DEFAULT NULL,
    `update_time` TIMESTAMP        DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
```

### 2. 修改配置

编辑 `flashdeal-core/src/main/resources/application-dev.yaml`，按实际环境调整 MySQL、Redis、RocketMQ 地址：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flash_deal?sslMode=DISABLED&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      # password: your_password  # 如 Redis 有密码请取消注释

rocketmq:
  name-server: localhost:9876
  producer:
    group: voucherorder_group
```

### 3. 启动中间件

```bash
# 启动 Redis
redis-server --daemonize yes

# 启动 RocketMQ NameServer 与 Broker（需在 RocketMQ 安装目录执行）
nohup sh bin/mqnamesrv &
nohup sh bin/mqbroker -n localhost:9876 &
```

### 4. 构建与运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar flashdeal-core/target/FlashDeal-1.0.0.jar

# 或开发模式
mvn spring-boot:run
```

应用启动后监听 **8080** 端口。

---

## 📡 接口文档

### 用户登录

```http
POST /user/login
Content-Type: application/json

{
  "phone": "13800138000"
}
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "id": 1,
    "phone": "13800138000",
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 添加秒杀优惠券（测试用）

```http
POST /test/voucher/seckill
Content-Type: application/json

{
  "title": "100元代金券",
  "subTitle": "限时秒杀",
  "payValue": 8000,
  "actualValue": 10000,
  "type": 1,
  "status": 1,
  "stock": 100,
  "beginTime": "2026-06-29T10:00:00",
  "endTime": "2026-06-29T12:00:00"
}
```

### 秒杀下单（核心接口）

```http
POST /user/seckill/{id}
authentication: <登录返回的 token>
```

**响应示例：**

**成功（正在抢购中）：**

```json
{
  "code": 1,
  "msg": null,
  "data": "正在抢购中"
}
```

> 秒杀接口返回“正在抢购中”后，前端通过状态查询接口轮询最终结果。

### 查询秒杀订单状态

```http
GET /user/seckill/status/{voucherId}
authentication: <登录返回的 token>
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": "SUCCESS"
}
```

**状态说明：**

| 状态           | 含义                                |
|:-------------|:----------------------------------|
| `PROCESSING` | 订单正在处理中                           |
| `SUCCESS`    | 订单创建成功                            |
| `FAILED`     | 订单创建失败（已回滚库存）或 DB 无订单且 Redis 状态丢失 |

**库存不足：**

```json
{
  "code": 0,
  "msg": "优惠券已卖完",
  "data": null
}
```

**重复下单：**

```json
{
  "code": 0,
  "msg": "不能重复下单",
  "data": null
}
```

**限流拦截（HTTP 429）：**

```json
{
  "code": 0,
  "msg": "RATE_LIMIT",
  "data": null
}
```

**异常降级：**

```json
{
  "code": 0,
  "msg": "抢购失败，请稍后重试",
  "data": null
}
```

---

## 🔑 关键设计解析

### 1. Lua 脚本原子操作

秒杀场景下，库存校验、去重、库存扣减如果分开执行，会出现并发竞态。本项目通过两个 Lua 脚本分别覆盖正向预扣与失败回滚，所有操作在
Redis 单线程内原子完成：

**seckill.lua — 原子预扣（3 个参数）**

```lua
local stockKey = KEYS[1]      -- 库存 key: seckill:{id}:stock
local orderKey = KEYS[2]      -- 已购用户集合 key: seckill:{id}:order
local userId = ARGV[1]      -- 用户 ID

-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey) or 0) <= 0) then
    return 1                  -- 库存不足
end

if (redis.call('sadd', orderKey, userId) == 0) then
    return 2                  -- sadd 返回0，说明这个用户已经在集合里了，重复下单
end

redis.call('decr', stockKey)  -- 扣减库存
return 0                      -- 成功
```

**rollback.lua — 原子回滚（6 个参数）**

```lua
local stockKey = KEYS[1]      -- 库存 key
local orderKey = KEYS[2]      -- 已购用户集合 key
local idempotencyKey = KEYS[3]      -- 幂等键: seckill:{voucherId}:userId:consumed
local userId = ARGV[1]      -- 用户 ID
local mode = ARGV[2]      -- "DELETE" 清除状态 / "FAIL" 标记失败

-- 回补库存
redis.call('incr', stockKey)
-- 移除用户购买记录
redis.call('srem', orderKey, userId)

-- 处理幂等 key
if mode == "DELETE" then
    redis.call('del', idempotencyKey)     -- MQ 发送失败：清除幂等键
    return 1
end

-- FAIL 模式：标记失败并设置过期时间（3600秒 = 1小时）
local ttlSeconds = ARGV[3]
redis.call('set', idempotencyKey, "FAILED", "EX", tonumber(ttlSeconds))
return 1
```

> **关键点**：
> - 预扣脚本先 `sadd` 再 `decr`，相比 `sismember` + `sadd` + `decr` 三步，减少一次 Redis 调用，同时利用 `sadd` 的返回值天然完成判重
> - 回滚脚本支持两种模式：`FAIL` 模式标记失败并设置 TTL（1 小时），回滚库存+移除购买记录，用户可立即重试；`DELETE`
    模式直接清除幂等键，用于彻底清理状态

### 2. 全局唯一订单 ID

订单 ID 不能用 MySQL 自增（分库分表场景会冲突），也不能用 UUID（无序，影响 B+ 树插入性能）。本项目采用 **Hutool Snowflake 雪花算法
**：

```
|  1 bit 符号位  |  41 bit 时间戳  |  10 bit 机器 ID  |  12 bit 序列号  |
```

- 41 位时间戳：毫秒级，可用约 69 年
- 10 位机器 ID：支持 1024 个节点（单机部署写死 0，分布式场景从配置中心读取）
- 12 位序列号：同一毫秒内最多生成 4096 个 ID

趋势递增、本地生成、性能极高，天然适合高并发订单号场景。

### 3. Redis 与 DB 最终一致性

系统采用"**Redis 预扣 + MQ 异步落库**"模式，Redis 是库存的“快”视图，DB 是“真”数据源。一致性保障措施：

- **MQ 异步发送**：`sendOrderAsync()` 内部调用 RocketMQ `asyncSend()`，立即返回"正在抢购中"。发送失败时（同步异常或异步回调失败）均通过
  `rollback.lua` 原子回滚 Redis（库存+资格一次完成）并标记幂等键为 FAILED，用户可立即重试
- **三态幂等**：`PROCESSING/SUCCESS/FAILED` 三态设计，消费端读取幂等键状态，只有终态（SUCCESS/FAILED）才跳过，PROCESSING 继续处理
- **终态判断**：消费端先查 Redis 状态，若为 SUCCESS/FAILED 直接跳过；若状态丢失则进入兜底流程
- **DB 乐观锁兜底**：`WHERE stock > 0` 防止超卖，DB 层再次校验防重复（唯一索引 `uk_user_voucher`）
- **消费端异常分级**：确定性失败（BusinessException）直接终结并回滚；偶发性异常抛出让 MQ 重试（最多 3 次）
- **订单落库检查**：`handleFail()` 先查 DB 确认订单状态，已落库则标记 SUCCESS 不回滚，未落库通过 `rollback.lua` 原子回滚库存并标记
  FAILED

### 4. 分层防御体系

- **第一层（入口限流）**：Redisson `RRateLimiter` 全局限流 3000 req/s，超出直接返回 HTTP 429 + "RATE_LIMIT"
- **第二层（快速失败）**：Redis Lua 脚本原子预扣，瞬间拒绝无库存/重复请求，不进入后续流程
- **第三层（数据兜底）**：DB 乐观锁 `WHERE stock > 0` + 唯一索引 `uk_user_voucher` 确保最终数据一致性

### 5. MQ 消费与容错机制

- **异步发送**：`sendOrderAsync()` 使用 RocketMQ `asyncSend()`，成功回调记录日志，失败回调触发回滚
- **失败回滚**：MQ 发送失败时（同步异常由 ServiceImpl 捕获、异步回调失败由 `onException` 处理）均调用 `rollback.lua`
  原子回滚（库存+1、移除用户购买记录）并标记幂等键为 FAILED（TTL 1 小时），用户可立即重试
- **消费端幂等**：读取 Redis 幂等键状态，SUCCESS/FAILED 终态跳过，PROCESSING 继续处理
- **异常分级处理**：
    - 业务异常（BusinessException）：确定性失败，调用 `handleFail()` 检查 DB 是否已落库，未落库则回滚并标记 FAILED
    - 系统异常：抛出异常触发 MQ 重试，最多重试 3 次后进入死信队列

### 6. MQ 消费与订单创建

MQ 消息体从完整 `SeckillOrder` 精简为只含 `userId` 和 `voucherId` 的 `SeckillOrderMessage`，Producer 端不再构建订单对象，Consumer
端接收轻量消息后在 `createSeckillOrder` 方法中完成订单构建与 DB 落库。这种设计使消息更轻量，职责更清晰：

- **Producer**：只负责发送最小必要信息
- **Consumer**：接收消息后完成完整订单构建与持久化
- **Service**：专注业务逻辑，不感知 MQ 细节

> 这种设计避免了在 Controller 或 Service 层直接构造 MQ 消息体，保持了各层职责分离。

### 7. 定时对账兜底机制

MQ 重试耗尽后，订单可能卡在 `PROCESSING` 状态（消费者异常、进程崩溃等）。定时对账任务每 5 分钟执行一次，作为最终一致性兜底：

1. **SCAN 扫描**：遍历 Redis 中所有 `seckill:*:consumed` 幂等键，过滤出 `PROCESSING` 状态
2. **对比 DB**：解析 key 中的 `voucherId` 和 `userId`，查询 DB 订单是否存在
3. **修复不一致**：
    - DB 有订单 → Redis 修正为 `SUCCESS`（消费者已落库但标记前崩溃）
    - DB 无订单 → 执行 `rollback.lua` 原子回滚库存+资格，标记 `FAILED`（MQ 重试耗尽仍未落库）

> 5 分钟间隔的设定确保大于 MQ 3 次重试的总耗时（~4min），避免对账与 MQ 重试冲突导致库存多加。

---

## 🧪 压测脚本

项目内置风控压测脚本，位于 `flashdeal-core/scripts/` 目录：

```bash
# 进入脚本目录
cd flashdeal-core/scripts/

# 1. 批量注册压测用户
./register_test_users.sh

# 2. 区分用户画像（正常用户 / 羊毛党）
REDIS_PASS=865943 ./diversify_user_profiles.sh

# 3. 执行风控压测
wrk -t4 -c100 -d30s -s wrk_seckill_risk_test.lua http://localhost:8080
```

压测流程：

1. `register_test_users.sh` 批量注册用户并生成 `risk_tokens.txt`（5000 个 Token）
2. `diversify_user_profiles.sh` 按 userId%100 区分正常用户与羊毛党画像
3. `wrk_seckill_risk_test.lua` 自动分类统计：限流拦截、风控拦截、库存不足、重复下单、成功/处理中

**wrk 参数：**

- `-t4` 4 线程
- `-c100` 100 并发连接
- `-d30s` 持续 30 秒

**示例输出：**

```
========================================
总请求: 844121 | QPS: 28055
--- 业务分类 ---
限流拦截: 753718 (89.29%)
风控拦截: 86036 (10.19%)
库存不足: 4079 (0.48%)
重复下单: 188 (0.02%)
成功/处理中: 100 (0.01%)
延迟: avg=4.98ms p50=1.95ms p99=18.48ms
========================================
```

> **注意**：首次压测前需执行 `register_test_users.sh` 注册用户并生成 `risk_tokens.txt`，再执行 `diversify_user_profiles.sh` 区分用户画像。Token 有效期 2 小时，过期需重新生成。

---

## 🗺️ 路线图

- [x] 用户登录与 JWT 鉴权
- [x] 秒杀优惠券管理
- [x] Redis Lua 原子预扣
- [x] RocketMQ 异步落库
- [x] DB 乐观锁防重复
- [x] 三态幂等与状态查询接口
- [x] 失败回滚与 Lua 原子恢复
- [x] 接口压测脚本（wrk）
- [x] 定时对账任务（PROCESSING 卡单兜底修复）
- [x] 风控模块（特征提取 + 决策树模型 + 自动装配）
- [ ] Docker Compose 一键部署

---

## 📄 License

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源，欢迎学习、交流与二次开发。

---

<div align="center">

**如果这个项目对你有帮助，欢迎 ⭐ Star 支持！**

</div>
