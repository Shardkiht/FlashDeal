package com.flashdeal.riskguard.feature.impl;

import com.flashdeal.riskguard.dto.RiskRequest;
import com.flashdeal.riskguard.feature.FeatureExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 秒杀场景特征提取实现
 */
@Slf4j
public class SeckillFeatureExtractor implements FeatureExtractor {

    private static final String BUSINESS_TYPE = "SECKILL";

    /** 账号注册时间戳 key 前缀，写入时机见 UserServiceImpl 自动注册分支 */
    private static final String REG_TIME_KEY_PREFIX = "risk:regtime:";
    /** 历史订单数计数器 key 前缀，写入时机见 SeckillConsumer.onMessage() */
    private static final String ORDER_COUNT_KEY_PREFIX = "risk:orderCount:";

    private final StringRedisTemplate redisTemplate;

    /** Lua 脚本设置 key 过期时间（秒），绕过 DefaultedRedisConnection 的 expire/pExpire 循环代理 bug */
    private static final DefaultRedisScript<String> EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('EXPIRE', KEYS[1], ARGV[1])", String.class);

    public SeckillFeatureExtractor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String supportBusinessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public double[] extract(RiskRequest request) {
        Long userId = request.getUserId();
        String ip = request.getClientIp();
        String userAgent = request.getUserAgent();

        // Redis Pipeline 批量写入行为记录（qps计数、ip段集合、点击时间戳）
        String qpsKey = "risk:qps:" + ip;
        String ipSegKey = "risk:ipseg:" + getIpSegment(ip);
        String clicksKey = "risk:clicks:" + userId;

        redisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    byte[] qpsBytes = rawKey(qpsKey);
                    byte[] ipSegBytes = rawKey(ipSegKey);
                    byte[] clicksBytes = rawKey(clicksKey);
                    byte[] userIdBytes = String.valueOf(userId).getBytes();
                    byte[] tsBytes = nowBytes();

                    connection.incr(qpsBytes);

                    connection.sAdd(ipSegBytes, userIdBytes);

                    connection.listCommands().lPush(clicksBytes, tsBytes);
                    connection.listCommands().lTrim(clicksBytes, 0, 9);

                    return null;
                });

        // 用 Lua EVAL 设置过期时间，完全绕过 DefaultedRedisConnection 的 expire/pExpire 循环代理
        redisTemplate.execute(EXPIRE_SCRIPT, List.of(qpsKey), String.valueOf(1));
        redisTemplate.execute(EXPIRE_SCRIPT, List.of(ipSegKey), String.valueOf(300));
        redisTemplate.execute(EXPIRE_SCRIPT, List.of(clicksKey), String.valueOf(60));

        String qpsStr = redisTemplate.opsForValue().get(qpsKey);
        double qpsPerIp = parseDouble(qpsStr);

        Long ipSegCard = redisTemplate.opsForSet().size(ipSegKey);
        double ipSimilarity = Math.min(1.0, (ipSegCard == null ? 1L : ipSegCard) / 10.0);

        List<String> clickTimestamps = redisTemplate.opsForList().range(clicksKey, 0, -1);
        double clickIntervalStdMs = calcClickIntervalStd(clickTimestamps);

        // accountAgeDays / orderHistory 改为读 Redis，不再查 DB
        int accountAgeDays = getAccountAgeDays(userId);
        int orderHistory = getOrderHistory(userId);

        int isEmulator = isEmulator(userAgent) ? 1 : 0;

        return new double[]{qpsPerIp, ipSimilarity, accountAgeDays, orderHistory, clickIntervalStdMs, isEmulator};
    }

    /** 从注册时间戳计算账号天数；查不到（老用户/迁移前数据）给默认值 365，倾向判定为正常老用户 */
    private int getAccountAgeDays(Long userId) {
        try {
            String regTimeStr = redisTemplate.opsForValue().get(REG_TIME_KEY_PREFIX + userId);
            if (regTimeStr == null) {
                return 365;
            }
            long regTime = Long.parseLong(regTimeStr);
            long days = (System.currentTimeMillis() - regTime) / (1000L * 60 * 60 * 24);
            return (int) days;
        } catch (Exception e) {
            log.warn("读取 accountAgeDays 失败, userId={}, 使用默认值 365", userId, e);
            return 365;
        }
    }

    /** 从计数器读取历史订单数；查不到给默认值 0（新用户合理默认） */
    private int getOrderHistory(Long userId) {
        try {
            String countStr = redisTemplate.opsForValue().get(ORDER_COUNT_KEY_PREFIX + userId);
            return countStr == null ? 0 : Integer.parseInt(countStr);
        } catch (Exception e) {
            log.warn("读取 orderHistory 失败, userId={}, 使用默认值 0", userId, e);
            return 0;
        }
    }

    private byte[] rawKey(String key) {
        return Objects.requireNonNull(redisTemplate.getStringSerializer()).serialize(key);
    }

    private byte[] nowBytes() {
        return String.valueOf(System.currentTimeMillis()).getBytes();
    }

    private String getIpSegment(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    private double parseDouble(Object val) {
        if (val == null) return 0.0;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double calcClickIntervalStd(List<String> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return 1000.0;
        try {
            List<Long> tsList = timestamps.stream().map(Long::parseLong).sorted().toList();
            List<Long> diffs = new ArrayList<>();
            for (int i = 1; i < tsList.size(); i++) {
                diffs.add(tsList.get(i) - tsList.get(i - 1));
            }
            double mean = diffs.stream().mapToLong(Long::longValue).average().orElse(1000.0);
            double variance = diffs.stream().mapToLong(Long::longValue).mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
            return Math.sqrt(variance);
        } catch (Exception e) {
            return 1000.0;
        }
    }

    private boolean isEmulator(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return false;
        String lower = userAgent.toLowerCase();
        return lower.contains("headlesschrome") || lower.contains("phantomjs")
                || lower.contains("selenium") || lower.contains("webdriver");
    }
}
