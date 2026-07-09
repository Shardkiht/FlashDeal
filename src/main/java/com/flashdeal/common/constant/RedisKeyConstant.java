package com.flashdeal.common.constant;

/**
 * Redis Key 常量
 * 秒杀相关 Redis Key 常量。
 */
public class RedisKeyConstant {

    // 秒杀券库存
    public static String getSeckillStockKey(Long id) {
        return "seckill:{" + id + "}:stock";
    }

    // 秒杀券订单
    public static String getSeckillOrderKey(Long id) {
        return "seckill:{" + id + "}:order";
    }

    // 幂等/状态 key，PROCESSING/SUCCESS/FAILED 三态复用
    public static String getConsumedKey(Long userId, Long voucherId) {
        return "seckill:{" + userId + "}:{" + voucherId + "}:consumed";
    }

    // 限流器 key
    public static final String LIMITER_KEY = "seckill:limiter";
}