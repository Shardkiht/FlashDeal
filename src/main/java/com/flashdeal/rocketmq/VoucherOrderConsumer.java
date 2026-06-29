package com.flashdeal.rocketmq;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.domain.VoucherOrder;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 秒杀订单 MQ 消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MessageConstant.VOUCHER_ORDER_TOPIC,
        consumerGroup = MessageConstant.VOUCHER_ORDER_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadMax = 64,
        maxReconsumeTimes = 3
)
@RequiredArgsConstructor
public class VoucherOrderConsumer implements RocketMQListener<VoucherOrder> {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(VoucherOrder order) {
        String idempotencyKey = "seckill:consumed:" + order.getId();

        // 1. 幂等校验
        Boolean consumed = stringRedisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(consumed)) {
            log.warn("订单已处理, orderId={}", order.getId());
            return;
        }

        // 2. 处理订单
        try {
            voucherOrderService.createVoucherOrder(order);
        } catch (BusinessException e) {
            log.error("业务异常, orderId={}", order.getId(), e);
            recordBusinessFail(order, e.getMessage());
        } catch (Exception e) {
            log.error("系统异常, orderId={}", order.getId(), e);
            throw e;
        }
    }

    private void recordBusinessFail(VoucherOrder order, String reason) {
        long count = voucherOrderService.query().eq("id", order.getId()).count();

        if (count == 0) {
            String stockKey = RedisKeyConstant.getSeckillVoucherStockKey(order.getVoucherId());
            String orderKey = RedisKeyConstant.getSeckillVoucherOrderKey(order.getVoucherId());
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(orderKey, String.valueOf(order.getUserId()));
            stringRedisTemplate.delete("seckill:consumed:" + order.getId());

            SeckillFailRecord record = new SeckillFailRecord();
            log.error("业务异常订单进入核查队列: orderId={}, userId={}, voucherId={}, reason={}",
                    order.getId(), order.getUserId(), order.getVoucherId(), reason);
        }
    }
}