package com.flashdeal.common.constant;

/**
 * 信息提示常量类
 * 仅保留登录与秒杀相关常量，删除外卖业务相关提示。
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
}