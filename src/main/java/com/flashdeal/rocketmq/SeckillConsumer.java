package com.flashdeal.rocketmq;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.domain.SeckillOrder;
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
@RocketMQMessageListener(
        topic = MessageConstant.VOUCHER_ORDER_TOPIC,
        consumerGroup = MessageConstant.VOUCHER_ORDER_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadMax = SeckillConstant.CONSUME_THREAD_MAX,
        maxReconsumeTimes = SeckillConstant.MAX_RECONSUME_TIMES
)
@RequiredArgsConstructor
public class SeckillConsumer implements RocketMQListener<SeckillOrder> {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_ROLLBACK_SCRIPT, Long.class);

    private final SeckillService seckillService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(SeckillOrder order) {
        String idempotencyKey = RedisKeyConstant.getConsumedKey(order.getUserId(), order.getVoucherId());

        // 读取当前状态，只有终态(SUCCESS/FAILED)才跳过
        String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
        if (SeckillConstant.STATUS_SUCCESS.equals(status) || SeckillConstant.STATUS_FAILED.equals(status)) {
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
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_SUCCESS);
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
        // 1. 查库确认订单是否已落库
        long count = seckillService.query().eq("id", order.getId()).count();

        if (count > 0) {
            // 订单已存在，标记 SUCCESS
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_SUCCESS);
            return;
        }

        // 2. 订单未写入，回滚 Redis
        String stockKey = RedisKeyConstant.getSeckillStockKey(order.getVoucherId());
        String orderKey = RedisKeyConstant.getSeckillOrderKey(order.getVoucherId());
        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Arrays.asList(stockKey, orderKey, idempotencyKey),
                String.valueOf(order.getUserId()), SeckillConstant.ROLLBACK_RESULT_FAIL, SeckillConstant.ROLLBACK_EXPIRE_SECONDS
        );
        log.error("订单未写入，已回滚, orderId={}, reason={}", order.getId(), reason);
    }
}