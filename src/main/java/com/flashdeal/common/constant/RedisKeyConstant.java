package com.flashdeal.common.constant;

/**
 * Redis Key 常量
 * 秒杀相关 Redis Key 常量。
 */
public class RedisKeyConstant {

    // 秒杀优惠券库存
    public static String getSeckillVoucherStockKey(Long id) {
        return "seckill:{" + id + "}:stock";
    }

    // 秒杀优惠券订单
    public static String getSeckillVoucherOrderKey(Long id) {
        return "seckill:{" + id + "}:order";
    }

    // 幂等/状态 key，PROCESSING/SUCCESS/FAILED 三态复用
    public static String getConsumedKey(Long orderId) {
        return "seckill:{" + orderId + "}:consumed";
    }
}