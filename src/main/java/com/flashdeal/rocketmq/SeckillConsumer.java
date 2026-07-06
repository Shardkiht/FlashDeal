package com.flashdeal.rocketmq;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.domain.SeckillOrder;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.service.api.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 秒杀订单 MQ 消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MessageConstant.VOUCHER_ORDER_TOPIC,
        consumerGroup = MessageConstant.VOUCHER_ORDER_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadMax = 32,
        maxReconsumeTimes = 3
)
@RequiredArgsConstructor
public class SeckillConsumer implements RocketMQListener<SeckillOrder> {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load("lua/rollback.lua", Long.class);

    private final SeckillService seckillService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(SeckillOrder order) {
        String idempotencyKey = RedisKeyConstant.getConsumedKey(order.getUserId(), order.getVoucherId());

        // 读取当前状态，只有终态(SUCCESS/FAILED)才跳过
        String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            log.info("订单已是终态，跳过, orderId={}, status={}", order.getId(), status);
            return;
        }

        // Redis 状态丢失，回滚库存让用户重试
        if (null == status) {
            log.warn("Redis 状态丢失，进入兜底处理, orderId={}", order.getId());
            handleFail(order, idempotencyKey, "Redis状态丢失");
            return;
        }

        try {
            seckillService.createSeckillOrder(order);
            stringRedisTemplate.opsForValue().set(idempotencyKey, "SUCCESS");
        } catch (BusinessException e) {
            // 确定性失败：不重试，直接终结
            log.error("业务异常, orderId={}", order.getId(), e);
            handleFail(order, idempotencyKey, e.getMessage());
        } catch (Exception e) {
            // 偶发性失败：继续抛出交给 MQ 重试，状态仍是 PROCESSING
            log.error("系统异常, orderId={}", order.getId(), e);
            throw e;
        }
    }

    private void handleFail(SeckillOrder order, String idempotencyKey, String reason) {
        // 1. 查库
        long count = seckillService.query().eq("id", order.getId()).count();

        if (count > 0) {
            // 2. 订单已存在，标记 SUCCESS
            try {
                stringRedisTemplate.opsForValue().set(idempotencyKey, "SUCCESS");
            } catch (Exception e) {
                log.error("标记SUCCESS失败, orderId={}, 前端查询请求任务兜底", order.getId(), e);
            }
            return;
        }

        // 3. 订单未写入，回滚 Redis
        String stockKey = RedisKeyConstant.getSeckillStockKey(order.getVoucherId());
        String orderKey = RedisKeyConstant.getSeckillOrderKey(order.getVoucherId());
        try {
            stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Arrays.asList(stockKey, orderKey, idempotencyKey),
                    String.valueOf(order.getUserId()), "FAIL", "86400"
            );
        } catch (Exception e) {
            log.error("回滚Redis失败, orderId={}, 前端查询请求任务兜底", order.getId(), e);
        }

        // 4. 记日志
        SeckillFailLog record = new SeckillFailLog(
                order.getId(), order.getUserId(), order.getVoucherId(), reason, LocalDateTime.now());
        log.error("订单未写入，已回滚，进入核查: {}", record);
    }
}