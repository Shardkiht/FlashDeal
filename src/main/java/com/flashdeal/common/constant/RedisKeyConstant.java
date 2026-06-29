package com.flashdeal.common.constant;

/**
 * Redis Key 常量
 * 仅保留秒杀相关 Key，删除外卖业务缓存 Key。
 */
public class RedisKeyConstant {

    public static final String VOUCHER_ORDER = "lock:voucher:order:";
    public static final String SECKILLVOUCHER_STOCK = "seckill:stock:";
    public static final String SECKILLVOUCHER_ORDER = "seckill:order:";

    public static String getVoucherOrderKey(Long userId) {
        return VOUCHER_ORDER + userId;
    }

    public static String getSeckillVoucherStockKey(Long id) {
        return "seckill:{" + id + "}:stock";
    }

    public static String getSeckillVoucherOrderKey(Long id) {
        return "seckill:{" + id + "}:order";
    }
}