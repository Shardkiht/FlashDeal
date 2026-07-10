package com.flashdeal.common.constant;

/**
 * 秒杀业务常量
 * 订单状态、支付类型、回滚 TTL 等。
 */
public class SeckillConstant {

    // ========== 订单状态（Redis 幂等/状态 key 的三态值） ==========
    /** 处理中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 秒杀成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 秒杀失败 */
    public static final String STATUS_FAILED = "FAILED";

    // ========== 回滚 Lua 脚本参数 ==========
    /** 回滚结果标识 */
    public static final String ROLLBACK_RESULT_FAIL = "FAIL";
    /** 回滚后 Redis key 过期时间（秒） */
    public static final String ROLLBACK_EXPIRE_SECONDS = "3600";

    // ========== 订单字段 ==========
    /** 支付方式：余额支付 */
    public static final int PAY_TYPE_BALANCE = 1;
    /** 订单状态：未支付 */
    public static final int ORDER_STATUS_UNPAID = 1;

    // ========== MQ 消费重试 ==========
    /** 最大重试次数 */
    public static final int MAX_RECONSUME_TIMES = 3;
    /** 最大消费线程数 */
    public static final int CONSUME_THREAD_MAX = 32;

    // ========== Lua 脚本路径 ==========
    public static final String LUA_SECKILL_SCRIPT = "lua/seckill.lua";
    public static final String LUA_ROLLBACK_SCRIPT = "lua/rollback.lua";

    // ========== 定时对账 ==========
    /** 对账任务执行间隔（毫秒），5 分钟 */
    public static final long RECONCILE_INTERVAL_MS = 5 * 60 * 1000L;

    // ========== 环境与数据库 ==========
    /** 开发环境 profile 名称 */
    public static final String PROFILE_DEV = "dev";
    /** 订单表列名：用户ID */
    public static final String COL_USER_ID = "user_id";
    /** 订单表列名：优惠券ID */
    public static final String COL_VOUCHER_ID = "voucher_id";
}
