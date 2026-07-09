package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.utils.SnowflakeIdGenerate;
import com.flashdeal.domain.Result;
import com.flashdeal.domain.SeckillOrder;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.mapper.SeckillOrderMapper;
import com.flashdeal.service.api.SeckillService;
import com.flashdeal.service.api.SeckillVoucherService;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.common.utils.UserHolder;
import com.flashdeal.rocketmq.SeckillProducer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 秒杀服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl extends ServiceImpl<SeckillOrderMapper, SeckillOrder> implements SeckillService {

    private final SnowflakeIdGenerate snowflakeIdGenerate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillProducer seckillProducer;
    private final SeckillVoucherService seckillVoucherService;
    private final Environment environment;

    @PostConstruct
    public void initSeckillStock() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains(SeckillConstant.PROFILE_DEV)) {
            log.info("非 dev 环境，跳过 Redis 库存初始化");
            return;
        }
        log.info("开始初始化秒杀库存到Redis...");
        var seckillVouchers = seckillVoucherService.list();

        for (var voucher : seckillVouchers) {
            String stockKey = RedisKeyConstant.getSeckillStockKey(voucher.getId());
            String orderKey = RedisKeyConstant.getSeckillOrderKey(voucher.getId());

            // 1. 先清空旧的库存和订单记录
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(orderKey);

            // 2. 从数据库读取库存并设置到Redis
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
            log.info("初始化秒杀券ID={}, 库存={}", voucher.getId(), voucher.getStock());
        }

        log.info("秒杀库存初始化完成，共{}个商品", seckillVouchers.size());
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_SECKILL_SCRIPT, Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_ROLLBACK_SCRIPT, Long.class);

    @Override
    public Result<String> seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getCurrentId();

        String stockKey = RedisKeyConstant.getSeckillStockKey(voucherId);
        String orderKey = RedisKeyConstant.getSeckillOrderKey(voucherId);
        String idempotencyKey = RedisKeyConstant.getConsumedKey(userId, voucherId);

        try {
            // 1. 执行 Lua 脚本判断购买资格并预扣减 Redis 库存
            long result = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Arrays.asList(stockKey, orderKey),
                    String.valueOf(userId)
            );

            // 2. 结果判断
            if (result != 0) {
                return Result.error(result == 1
                        ? MessageConstant.VOUCHER_INSUFFICIENT
                        : MessageConstant.REPEAT_ORDER);
            }

            // 3. 创建订单对象
            Long orderId = snowflakeIdGenerate.nextId();
            SeckillOrder seckillOrder = SeckillOrder.builder()
                    .id(orderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .payType(SeckillConstant.PAY_TYPE_BALANCE)
                    .status(SeckillConstant.ORDER_STATUS_UNPAID)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            // 4. MQ发送前先标记 PROCESSING，让用户及时感知这个订单正在处理
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_PROCESSING);

            // 5. 异步发送 MQ，失败回调自动回滚 Redis 库存
            log.info("开始异步发送MQ, orderId={}", orderId);
            seckillProducer.sendOrderAsync(seckillOrder, stockKey, orderKey, idempotencyKey);

            // 返回"处理中"，前端轮询用户最近的秒杀订单状态
            return Result.success(MessageConstant.SECKILL_PROCESSING_MSG);

            // 异常处理，回滚 Redis 库存
        } catch (Exception e) {
            log.error("秒杀失败, 回滚Redis, voucherId={}, userId={}", voucherId, userId, e);
            stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Arrays.asList(stockKey, orderKey, idempotencyKey),
                    String.valueOf(userId), SeckillConstant.ROLLBACK_RESULT_FAIL, SeckillConstant.ROLLBACK_EXPIRE_SECONDS
            );
            return Result.error(MessageConstant.SECKILL_FAIL_MSG);
        }
    }

    @Override
    @Transactional
    public void createSeckillOrder(SeckillOrder seckillOrder) {
        Long userId = seckillOrder.getUserId();
        Long voucherId = seckillOrder.getVoucherId();

        // 确保一个用户只能购买一次
        Long count = query().eq(SeckillConstant.COL_USER_ID, userId).eq(SeckillConstant.COL_VOUCHER_ID, voucherId).count();
        if (count > 0) {
            log.error(MessageConstant.REPEAT_ORDER);
            throw new BusinessException(MessageConstant.REPEAT_ORDER);
        }

        // 扣减库存，防止超卖
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            log.error(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
            throw new BusinessException(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
        }
        save(seckillOrder);

    }

    @Override
    public String querySeckillStatus(Long voucherId) {
        Long userId = UserHolder.getCurrentId();
        String idempotencyKey = RedisKeyConstant.getConsumedKey(userId, voucherId);

        // 1. 先查 Redis
        String status = stringRedisTemplate.opsForValue().get(idempotencyKey);

        // 2. 查数据库
        Long count = query().eq(SeckillConstant.COL_USER_ID, userId).eq(SeckillConstant.COL_VOUCHER_ID, voucherId).count();

        if (count > 0) {
            // 数据库有订单，Redis 不一致则修复
            if (!SeckillConstant.STATUS_SUCCESS.equals(status)) {
                stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_SUCCESS);
            }
            return SeckillConstant.STATUS_SUCCESS;
        }

        // 3. 数据库无订单
        if (SeckillConstant.STATUS_PROCESSING.equals(status)) {
            return SeckillConstant.STATUS_PROCESSING;
        }

        return SeckillConstant.STATUS_FAILED;
    }
}