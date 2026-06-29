package com.flashdeal.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 雪花算法 的全局唯一 ID 生成器
 * 用于生成秒杀订单号。
 */
@Component
public class SnowflakeIdGenerate {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 单机部署：workerId 写死 0，datacenterId 写死 0
    // 后续分布式：从配置中心或环境变量读取
    private final Snowflake snowflake = IdUtil.getSnowflake(0, 0);

    public long nextId() {
        return snowflake.nextId();
    }
}