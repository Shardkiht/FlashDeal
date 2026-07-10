package com.flashdeal.rocketmq;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.domain.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 秒杀订单 MQ 生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillProducer {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_ROLLBACK_SCRIPT, Long.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 同步发送订单消息，带超时控制
     *
     * @param message 订单消息（userId + voucherId）
     * @param timeout 超时时间（毫秒）
     * @return true-发送成功, false-发送失败
     */
    public boolean sendOrderSync(SeckillOrderMessage message, long timeout) {
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(
                    MessageConstant.VOUCHER_ORDER_TOPIC,
                    MessageBuilder.withPayload(message).build(),
                    timeout
            );
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("订单发送成功, userId={}, voucherId={}", message.getUserId(), message.getVoucherId());
                return true;
            }
            log.warn("订单发送状态异常, userId={}, voucherId={}, status={}", message.getUserId(), message.getVoucherId(), sendResult.getSendStatus());
            return false;
        } catch (Exception e) {
            log.error("订单发送异常, userId={}, voucherId={}", message.getUserId(), message.getVoucherId(), e);
            return false;
        }
    }

    /**
     * 异步发送订单消息，失败回调回滚 Redis
     *
     * @param message        订单消息（userId + voucherId）
     * @param stockKey       库存 key
     * @param orderKey       订单 key
     * @param idempotencyKey 幂等 key
     */
    public void sendOrderAsync(SeckillOrderMessage message, String stockKey, String orderKey, String idempotencyKey) {
        rocketMQTemplate.asyncSend(
                MessageConstant.VOUCHER_ORDER_TOPIC,
                MessageBuilder.withPayload(message).build(),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("异步发送成功, userId={}, voucherId={}", message.getUserId(), message.getVoucherId());
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("异步发送失败, 回滚Redis, userId={}, voucherId={}", message.getUserId(), message.getVoucherId(), e);
                        stringRedisTemplate.execute(
                                ROLLBACK_SCRIPT,
                                Arrays.asList(stockKey, orderKey, idempotencyKey),
                                String.valueOf(message.getUserId()), SeckillConstant.ROLLBACK_RESULT_FAIL, SeckillConstant.ROLLBACK_EXPIRE_SECONDS
                        );
                    }
                }
        );
    }
}