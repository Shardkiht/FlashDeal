package com.flashdeal.rocketmq;

import com.alibaba.fastjson.JSON;
import com.flashdeal.constant.MessageConstant;
import com.flashdeal.domain.VoucherOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 MQ 生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 同步发送订单消息，带超时控制
     *
     * @param order   订单
     * @param timeout 超时时间（毫秒）
     * @return true-发送成功, false-发送失败
     */
    public boolean sendOrderSync(VoucherOrder order, long timeout) {
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
     * 发送失败兜底：写入 Redis 补偿队列
     */
    public void saveToCompensateQueue(VoucherOrder order) {
        String failKey = "seckill:order:fail";
        stringRedisTemplate.opsForList().rightPush(failKey, JSON.toJSONString(order));
        log.warn("订单已加入补偿队列, orderId={}", order.getId());
    }
}