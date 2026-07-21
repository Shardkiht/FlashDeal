package com.flashdeal.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.domain.SeckillOrder;
import com.flashdeal.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单定时对账任务
 * <p>
 * 扫描 Redis 中处于 PROCESSING 状态的幂等键，对比数据库订单记录，修复不一致数据：
 * <ul>
 *   <li>DB 有订单、Redis 为 PROCESSING → 修复为 SUCCESS</li>
 *   <li>DB 无订单、Redis 为 PROCESSING → 回滚库存+资格，标记 FAILED</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillReconciliationTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * 每 5 分钟执行一次对账
     */
    @Scheduled(fixedRate = SeckillConstant.RECONCILE_INTERVAL_MS)
    public void reconcile() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisKeyConstant.CONSUMED_KEY_PATTERN)
                .count(200)
                .build();

        int stuckCount = 0;
        int fixedCount = 0;
        int alarmCount = 0;

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String status = stringRedisTemplate.opsForValue().get(key);

                // 只处理 PROCESSING:xxx 格式的键
                if (status == null || !status.startsWith(SeckillConstant.STATUS_PROCESSING + ":")) {
                    continue;
                }

                // 解析时间戳，检查是否逻辑过期（超过阈值才处理，避免误回滚还在投递的 MQ 消息）
                long createTime;
                try {
                    createTime = Long.parseLong(status.substring(status.lastIndexOf(':') + 1));
                } catch (NumberFormatException e) {
                    log.warn("对账解析时间戳失败, key={}, status={}", key, status);
                    continue;
                }
                long elapsedSeconds = (System.currentTimeMillis() - createTime) / 1000;
                if (elapsedSeconds < SeckillConstant.PROCESSING_LOGICAL_EXPIRE_SECONDS) {
                    log.debug("对账跳过未过期键, key={}, elapsed={}s", key, elapsedSeconds);
                    continue;
                }

                stuckCount++;

                // 解析 key: seckill:{voucherId}:userId:consumed
                Long voucherId = parseVoucherId(key);
                Long userId = parseUserId(key);

                if (voucherId == null || userId == null) {
                    log.error("对账解析 key 失败, key={}", key);
                    continue;
                }

                // 查库确认订单是否落库
                long count = seckillOrderMapper.selectCount(
                        new LambdaQueryWrapper<SeckillOrder>()
                                .eq(SeckillOrder::getUserId, userId)
                                .eq(SeckillOrder::getVoucherId, voucherId)
                );

                if (count > 0) {
                    // DB 有订单，Redis 卡在 PROCESSING → 修复为 SUCCESS
                    stringRedisTemplate.opsForValue().set(key, SeckillConstant.STATUS_SUCCESS);
                    fixedCount++;
                    log.info("对账修复: 订单已落库但 Redis 为 PROCESSING, userId={}, voucherId={}", userId, voucherId);
                } else {
                    // DB 无订单 → 不回滚，只告警，由死信队列人工处理
                    alarmCount++;
                    log.warn("对账告警: 订单超时未落库，待人工处理, key={}, userId={}, voucherId={}", key, userId, voucherId);
                }
            }
        }

        log.info("对账任务完成: 卡单={}, 修复={}, 告警={}", stuckCount, fixedCount, alarmCount);
    }

    /**
     * 从幂等键中解析 voucherId
     * key 格式: seckill:{voucherId}:userId:consumed
     */
    private Long parseVoucherId(String key) {
        try {
            int start = key.indexOf('{') + 1;
            int end = key.indexOf('}');
            return Long.parseLong(key.substring(start, end));
        } catch (Exception e) {
            log.error("解析 voucherId 失败, key={}", key);
            return null;
        }
    }

    /**
     * 从幂等键中解析 userId
     * key 格式: seckill:{voucherId}:userId:consumed
     */
    private Long parseUserId(String key) {
        try {
            int afterBrace = key.indexOf('}') + 2; // skip '}:' 
            int end = key.indexOf(':', afterBrace);
            return Long.parseLong(key.substring(afterBrace, end));
        } catch (Exception e) {
            log.error("解析 userId 失败, key={}", key);
            return null;
        }
    }
}
