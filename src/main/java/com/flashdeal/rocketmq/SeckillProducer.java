package com.flashdeal.rocketmq;

import com.alibaba.fastjson.JSON;
import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.domain.SeckillOrder;
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
            LuaScriptUtil.load("lua/rollback.lua", Long.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 同步发送订单消息，带超时控制
     *
     * @param order   订单
     * @param timeout 超时时间（毫秒）
     * @return true-发送成功, false-发送失败
     */
    public boolean sendOrderSync(SeckillOrder order, long timeout) {
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(
                    MessageConstant.VOUCHER_ORDER_TOPIC,
                    MessageBuilder.withPayload(order).build(),
                    timeout
            );
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("订单发送成功, orderId={}", order.getId());
                return true;
            }
            log.warn("订单发送状态异常, orderId={}, status={}", order.getId(), sendResult.getSendStatus());
            return false;
        } catch (Exception e) {
            log.error("订单发送异常, orderId={}", order.getId(), e);
            return false;
        }
    }

    /**
     * 异步发送订单消息，失败回调回滚 Redis
     *
     * @param order           订单
     * @param stockKey        库存 key
     * @param orderKey        订单 key
     * @param idempotencyKey  幂等 key
     */
    public void sendOrderAsync(SeckillOrder order, String stockKey, String orderKey, String idempotencyKey) {
        try {
            rocketMQTemplate.asyncSend(
                    MessageConstant.VOUCHER_ORDER_TOPIC,
                    MessageBuilder.withPayload(order).build(),
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("异步发送成功, orderId={}", order.getId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("异步发送失败, 回滚Redis库存, orderId={}", order.getId(), e);
                            try {
                                stringRedisTemplate.execute(
                                        ROLLBACK_SCRIPT,
                                        Arrays.asList(stockKey, orderKey, idempotencyKey),
                                        String.valueOf(order.getUserId()), "DELETE"
                                );
                            } catch (Exception ex) {
                                log.error("回滚Redis失败, orderId={}, 定时任务兜底", order.getId(), ex);
                            }

                            // 发送失败，落盘到补偿队列以便人工/定时重试
                            try {
                                saveToCompensateQueue(order);
                            } catch (Exception qex) {
                                log.error("加入补偿队列失败, orderId={}", order.getId(), qex);
                            }
                        }
                    }
            );
        } catch (Exception e) {
            // asyncSend 本身可能在注册回调前抛出（序列化、客户端未就绪等），需要同步处理
            log.error("异步发送抛出同步异常，回滚 Redis 并加入补偿队列, orderId={}", order.getId(), e);
            try {
                stringRedisTemplate.execute(
                        ROLLBACK_SCRIPT,
                        Arrays.asList(stockKey, orderKey, idempotencyKey),
                        String.valueOf(order.getUserId()), "DELETE"
                );
            } catch (Exception rex) {
                log.error("回滚Redis失败, orderId={}", order.getId(), rex);
            }
            try {
                saveToCompensateQueue(order);
            } catch (Exception qex) {
                log.error("加入补偿队列失败, orderId={}", order.getId(), qex);
            }
        }
    }

    /**
     * 发送失败兜底：写入 Redis 补偿队列
     */
    public void saveToCompensateQueue(SeckillOrder order) {
        String failKey = "seckill:order:fail";
        stringRedisTemplate.opsForList().rightPush(failKey, JSON.toJSONString(order));
        log.warn("订单已加入补偿队列, orderId={}", order.getId());
    }
}