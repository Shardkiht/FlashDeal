package com.flashdeal.common.constant;

/**
 * Redis Key 常量
 * 秒杀相关 Redis Key 常量。
 */
public class RedisKeyConstant {

    public static String getSeckillVoucherStockKey(Long id) {
        return "seckill:{" + id + "}:stock";
    }

    public static String getSeckillVoucherOrderKey(Long id) {
        return "seckill:{" + id + "}:order";
    }
}