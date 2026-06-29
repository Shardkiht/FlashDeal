package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.constant.MessageConstant;
import com.flashdeal.constant.RedisKeyConstant;
import com.flashdeal.domain.Result;
import com.flashdeal.domain.VoucherOrder;
import com.flashdeal.exception.BusinessException;
import com.flashdeal.mapper.VoucherOrderMapper;
import com.flashdeal.rocketmq.VoucherOrderProducer;
import com.flashdeal.service.ISeckillVoucherService;
import com.flashdeal.service.IVoucherOrderService;
import com.flashdeal.utils.LuaScriptUtil;
import com.flashdeal.utils.RedisIdGenerate;
import com.flashdeal.utils.UserHolder;
import io.lettuce.core.RedisCommandTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final RedisIdGenerate redisIdGenerate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final VoucherOrderProducer voucherOrderProducer;
    private final ISeckillVoucherService seckillVoucherService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT =
            LuaScriptUtil.load("lua/seckill.lua", Long.class);

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long orderId = redisIdGenerate.generateId(RedisKeyConstant.SECKILLVOUCHER_ORDER);
        Long userId = UserHolder.getCurrentId();

        String stockKey = RedisKeyConstant.getSeckillVoucherStockKey(voucherId);
        String orderKey = RedisKeyConstant.getSeckillVoucherOrderKey(voucherId);

        try {
            // 1. 执行 Lua 脚本判断购买资格并预扣减 Redis 库存
            Long result = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Arrays.asList(stockKey, orderKey),
                    String.valueOf(userId),
                    String.valueOf(orderId)
            );

            // 2. 结果判断
            if (result == null || result != 0) {
                return Result.error(result != null && result == 1
                        ? MessageConstant.VOUCHER_INSUFFICIENT
                        : MessageConstant.REPEAT_ORDER);
            }

            // 3. 创建订单对象
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .id(orderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .payType(1)
                    .status(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            // 4. 同步发送 MQ，失败立即回滚 Redis 库存
            boolean sent = voucherOrderProducer.sendOrderSync(voucherOrder, 3000);
            if (!sent) {
                log.warn("MQ发送失败，回滚Redis库存，orderId={}", orderId);
                stringRedisTemplate.opsForValue().increment(stockKey);
                stringRedisTemplate.opsForSet().remove(orderKey, String.valueOf(userId));
                return Result.error("当前系统繁忙，请稍后重试");
            }

            return Result.success(orderId);

        } catch (RedisConnectionFailureException | RedisCommandTimeoutException | RedisSystemException e) {
            log.error("Redis 熔断，voucherId={}, userId={}", voucherId, userId, e);
            return Result.error("当前系统繁忙，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        RLock lock = redissonClient.getLock(RedisKeyConstant.getVoucherOrderKey(userId));
        boolean getLock = false;
        try {
            getLock = lock.tryLock(1000, 5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!getLock) {
            log.error(MessageConstant.REPEAT_ORDER);
            throw new RuntimeException(MessageConstant.REPEAT_ORDER);
        }

        try {
            // 确保一个用户只能购买一次
            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                log.error(MessageConstant.REPEAT_ORDER);
                throw new BusinessException(MessageConstant.REPEAT_ORDER);
            }

            // 扣减库存，防止超卖
            boolean result = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!result) {
                log.error(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
                throw new BusinessException(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
            }
            save(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
}