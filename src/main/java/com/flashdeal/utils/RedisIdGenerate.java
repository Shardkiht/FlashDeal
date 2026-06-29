package com.flashdeal.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 的全局唯一 ID 生成器
 * 用于生成秒杀订单号。
 */
@Component
public class RedisIdGenerate {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 起始时间戳
    private static final long BEGIN_TIMESTAMP = LocalDateTime
            .of(2026, 1, 1, 0, 0, 0)
            .toEpochSecond(ZoneOffset.UTC);

    public long generateId(String keyPrefix) {
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 生成自增长序列号
        Long increment = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + timestamp + date);

        return timestamp << 32 | increment;
    }
}