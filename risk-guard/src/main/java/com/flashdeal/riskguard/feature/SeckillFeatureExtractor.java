package com.flashdeal.riskguard.feature;

import com.flashdeal.riskguard.api.RiskRequest;
import com.flashdeal.riskguard.datasource.AccountInfoDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀场景特征提取实现
 */
@Slf4j
public class SeckillFeatureExtractor implements FeatureExtractor {

    private static final String BUSINESS_TYPE = "SECKILL";

    private final StringRedisTemplate redisTemplate;
    private final AccountInfoDao accountInfoDao;

    public SeckillFeatureExtractor(StringRedisTemplate redisTemplate, AccountInfoDao accountInfoDao) {
        this.redisTemplate = redisTemplate;
        this.accountInfoDao = accountInfoDao;
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

        // Redis Pipeline 批量操作
        String qpsKey = "risk:qps:" + ip;
        String ipSegKey = "risk:ipseg:" + getIpSegment(ip);
        String clicksKey = "risk:clicks:" + userId;

        redisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    byte[] qpsBytes = redisTemplate.getStringSerializer().serialize(qpsKey);
                    byte[] ipSegBytes = redisTemplate.getStringSerializer().serialize(ipSegKey);
                    byte[] clicksBytes = redisTemplate.getStringSerializer().serialize(clicksKey);
                    byte[] userIdBytes = String.valueOf(userId).getBytes();
                    byte[] tsBytes = String.valueOf(System.currentTimeMillis()).getBytes();

                    // qpsPerIp: INCR + EXPIRE
                    connection.incr(qpsBytes);
                    connection.expire(qpsBytes, 1);

                    // ipSimilarity: SADD + EXPIRE
                    connection.sAdd(ipSegBytes, userIdBytes);
                    connection.expire(ipSegBytes, 300);

                    // clickIntervalStdMs: LPUSH + LTRIM + EXPIRE
                    connection.listCommands().lPush(clicksBytes, tsBytes);
                    connection.listCommands().lTrim(clicksBytes, 0, 9);
                    connection.expire(clicksBytes, 60);

                    return null;
                });

        // 单独读取需要的值
        String qpsStr = redisTemplate.opsForValue().get(qpsKey);
        double qpsPerIp = parseDouble(qpsStr, 0.0);

        Long ipSegCard = redisTemplate.opsForSet().size(ipSegKey);
        double ipSimilarity = Math.min(1.0, (ipSegCard == null ? 1L : ipSegCard) / 10.0);

        List<String> clickTimestamps = redisTemplate.opsForList().range(clicksKey, 0, -1);
        double clickIntervalStdMs = calcClickIntervalStd(clickTimestamps);

        // MySQL 独立数据源查 accountAgeDays 和 orderHistory
        int accountAgeDays = 365;
        int orderHistory = 10;
        try {
            accountAgeDays = accountInfoDao.queryAccountAgeDays(userId);
        } catch (Exception e) {
            log.warn("查询 accountAgeDays 失败, userId={}, 使用默认值 365", userId, e);
        }
        try {
            orderHistory = accountInfoDao.queryOrderCount(userId);
        } catch (Exception e) {
            log.warn("查询 orderHistory 失败, userId={}, 使用默认值 10", userId, e);
        }

        // isEmulator：从 User-Agent 判断
        int isEmulator = isEmulator(userAgent) ? 1 : 0;

        return new double[]{qpsPerIp, ipSimilarity, accountAgeDays, orderHistory, clickIntervalStdMs, isEmulator};
    }

    private String getIpSegment(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    private double parseDouble(Object val, double defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private double calcClickIntervalStd(List<String> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return 1000.0;
        try {
            List<Long> tsList = timestamps.stream().map(Long::parseLong).sorted().collect(Collectors.toList());
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
        if (userAgent == null || userAgent.isEmpty()) return true;
        String lower = userAgent.toLowerCase();
        return lower.contains("headlesschrome") || lower.contains("phantomjs")
                || lower.contains("selenium") || lower.contains("webdriver");
    }
}
