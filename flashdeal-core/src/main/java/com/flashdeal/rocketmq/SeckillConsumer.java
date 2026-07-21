package com.flashdeal.rocketmq;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.domain.dto.SeckillOrderMessage;
import com.flashdeal.service.api.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 秒杀订单 MQ 消费者
 */
@Slf4j
@Component
/*
  RocketMQ 消费者监听配置，用于异步处理秒杀订单消息。

  @param topic              监听的消息主题，对应秒杀券下单 Topic（voucher-order-topic）
 * @param consumerGroup      消费者组名称，同一组内的消费者负载均衡消费消息（voucherorder_group）
 * @param messageModel       消息消费模式，CLUSTERING 表示集群消费，每条消息仅被组内一个消费者处理
 * @param consumeThreadMax   最大消费线程数，控制并发处理上限（32）
 * @param maxReconsumeTimes  消息最大重试消费次数，超过后进入死信队列（3 次）
 */
@RocketMQMessageListener(
        topic = MessageConstant.VOUCHER_ORDER_TOPIC,
        consumerGroup = MessageConstant.VOUCHER_ORDER_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadMax = SeckillConstant.CONSUME_THREAD_MAX,
        maxReconsumeTimes = SeckillConstant.MAX_RECONSUME_TIMES
)
@RequiredArgsConstructor
public class SeckillConsumer implements RocketMQListener<SeckillOrderMessage> {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_ROLLBACK_SCRIPT, Long.class);

    private final SeckillService seckillService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(SeckillOrderMessage message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        String idempotencyKey = RedisKeyConstant.getConsumedKey(userId, voucherId);

        // 读取当前状态，只有终态(SUCCESS/FAILED)才跳过
        String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
        if (SeckillConstant.STATUS_SUCCESS.equals(status) || SeckillConstant.STATUS_FAILED.equals(status)) {
            log.info("订单已是终态，跳过, userId={}, voucherId={}, status={}", userId, voucherId, status);
            return;
        }

        // Redis 状态丢失，回滚库存让用户重试
        if (null == status) {
            log.warn("Redis 状态丢失，进入兜底处理, userId={}, voucherId={}", userId, voucherId);
            handleFail(userId, voucherId, idempotencyKey, "Redis状态丢失");
            return;
        }

        try {
            seckillService.createSeckillOrder(userId, voucherId);
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_SUCCESS);

            // 订单确认成功，历史订单数计数器 +1
            stringRedisTemplate.opsForValue().increment("risk:orderCount:" + userId);
        } catch (BusinessException e) {
            // 确定性失败：不重试，直接终结
            log.error("业务异常, userId={}, voucherId={}", userId, voucherId, e);
            handleFail(userId, voucherId, idempotencyKey, e.getMessage());
        } catch (Exception e) {
            // 偶发性失败：继续抛出交给 MQ 重试，状态仍是 PROCESSING
            log.error("系统异常, userId={}, voucherId={}", userId, voucherId, e);
            throw e;
        }
    }

    private void handleFail(Long userId, Long voucherId, String idempotencyKey, String reason) {
        // 1. 查库确认订单是否已落库
        long count = seckillService.query()
                .eq(SeckillConstant.COL_USER_ID, userId)
                .eq(SeckillConstant.COL_VOUCHER_ID, voucherId)
                .count();

        if (count > 0) {
            // 订单已存在，标记 SUCCESS
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_SUCCESS);
            return;
        }

        // 2. 订单未写入，回滚 Redis
        String stockKey = RedisKeyConstant.getSeckillStockKey(voucherId);
        String orderKey = RedisKeyConstant.getSeckillOrderKey(voucherId);
        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Arrays.asList(stockKey, orderKey, idempotencyKey),
                String.valueOf(userId), SeckillConstant.ROLLBACK_RESULT_FAIL, SeckillConstant.ROLLBACK_EXPIRE_SECONDS
        );
        log.error("订单未写入，已回滚, userId={}, voucherId={}, reason={}", userId, voucherId, reason);
    }
}