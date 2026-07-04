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
import java.time.LocalDateTime;

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
public class VoucherOrderConsumer implements RocketMQListener<VoucherOrder> {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(VoucherOrder order) {
        String idempotencyKey = RedisKeyConstant.getConsumedKey(order.getUserId(), order.getVoucherId());

        // 不再用 setIfAbsent 当"门禁"，而是读取当前状态，只有终态(SUCCESS/FAILED)才跳过
        String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            log.warn("订单已是终态，跳过, orderId={}, status={}", order.getId(), status);
            return;
        }

        try {
            voucherOrderService.createVoucherOrder(order);
            stringRedisTemplate.opsForValue().set(idempotencyKey, "SUCCESS", Duration.ofHours(24));
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

    private void handleFail(VoucherOrder order, String idempotencyKey, String reason) {
        // 查询订单是否存在
        long count = voucherOrderService.query().eq("id", order.getId()).count();
        if (count > 0) {
            // 订单已经落库，异常发生在后续环节，不能回滚，标记成功即可
            stringRedisTemplate.opsForValue().set(idempotencyKey, "SUCCESS", Duration.ofHours(24));
            return;
        }

        // 订单未写入，回滚 Redis 库存与购买资格
        String stockKey = RedisKeyConstant.getSeckillVoucherStockKey(order.getVoucherId());
        String orderKey = RedisKeyConstant.getSeckillVoucherOrderKey(order.getVoucherId());
        stringRedisTemplate.opsForValue().increment(stockKey);
        stringRedisTemplate.opsForSet().remove(orderKey, String.valueOf(order.getUserId()));
        stringRedisTemplate.opsForValue().set(idempotencyKey, "FAILED", Duration.ofHours(24));

        // 结构化留痕，便于人工核查
        SeckillFailRecord record = new SeckillFailRecord(
                order.getId(), order.getUserId(), order.getVoucherId(), reason, LocalDateTime.now());
        log.error("订单未写入，已回滚，进入核查: {}", record);
    }
}