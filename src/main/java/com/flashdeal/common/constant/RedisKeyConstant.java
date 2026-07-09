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
    // hash tag 使用 voucherId，确保与 stockKey/orderKey 同槽，避免 Lua 脚本 CROSSSLOT
    public static String getConsumedKey(Long userId, Long voucherId) {
        return "seckill:{" + voucherId + "}:" + userId + ":consumed";
    }

    // 限流器 key
    public static final String LIMITER_KEY = "seckill:limiter";
}