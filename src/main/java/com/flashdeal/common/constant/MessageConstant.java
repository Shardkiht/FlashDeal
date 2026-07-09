package com.flashdeal.common.constant;

/**
 * 信息提示常量类
 * 登录与秒杀相关消息常量。
 */
public class MessageConstant {

    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String VOUCHER_INSUFFICIENT = "优惠券已卖完";
    public static final String REPEAT_ORDER = "不能重复下单";
    public static final String VOUCHER_ORDER_ERROR = "订单处理异常";
    public static final String VOUCHER_STOCK_NOT_ENOUGH = "优惠券数量不足";

    public static final String VOUCHER_ORDER_TOPIC = "voucher-order-topic";
    public static final String VOUCHER_ORDER_CONSUMER_GROUP = "voucherorder_group";

    // ========== 响应相关 ==========
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String SECKILL_FAIL_MSG = "抢购失败，请稍后重试";
    public static final String SECKILL_PROCESSING_MSG = "正在抢购中";
}