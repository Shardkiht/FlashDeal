package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.constant.SeckillConstant;
import com.flashdeal.common.utils.SnowflakeIdGenerate;
import com.flashdeal.domain.SeckillOrder;
import com.flashdeal.domain.dto.SeckillOrderMessage;
import com.flashdeal.common.exception.BusinessException;
import com.flashdeal.mapper.SeckillOrderMapper;
import com.flashdeal.service.api.SeckillService;
import com.flashdeal.service.api.SeckillVoucherService;
import com.flashdeal.common.utils.LuaScriptUtil;
import com.flashdeal.common.utils.UserHolder;
import com.flashdeal.riskguard.dto.RiskDecision;
import com.flashdeal.riskguard.api.RiskGuardClient;
import com.flashdeal.riskguard.dto.RiskRequest;
import com.flashdeal.rocketmq.SeckillProducer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
    private final RiskGuardClient riskGuardClient;

    @PostConstruct
    public void initSeckillStock() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains(SeckillConstant.PROFILE_DEV)) {
            log.info("非 dev 环境，跳过秒杀测试数据重置");
            return;
        }
        log.info("开始重置秒杀测试数据（DB库存/订单 + Redis）...");
        var seckillVouchers = seckillVoucherService.list();

        for (var voucher : seckillVouchers) {
            Long voucherId = voucher.getId();
            Integer resetStock = voucher.getInitialStock() != null ? voucher.getInitialStock() : voucher.getStock();

            // 1. DB库存字段重置回初始值
            seckillVoucherService.update()
                    .set("stock", resetStock)
                    .eq("id", voucherId)
                    .update();

            // 2. 清空该券的历史订单（避免"不能重复下单"挡住重复压测）
            // 注意：这里只清 voucher_order（业务订单表），不动 risk:orderCount（风控画像数据，跨压测保留）
            this.remove(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillOrder>()
                    .eq(SeckillOrder::getVoucherId, voucherId));

            // 3. 重置 Redis stockKey / orderKey
            String stockKey = RedisKeyConstant.getSeckillStockKey(voucherId);
            String orderKey = RedisKeyConstant.getSeckillOrderKey(voucherId);
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(orderKey);
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(resetStock));

            // 4. 清空该券所有用户的幂等状态key（用SCAN，避免KEYS阻塞Redis）
            clearConsumedKeys(voucherId);

            log.info("重置秒杀券ID={}, 库存重置为={}", voucherId, resetStock);
        }
        log.info("秒杀测试数据重置完成，共{}个商品", seckillVouchers.size());
    }

    private void clearConsumedKeys(Long voucherId) {
        String pattern = "seckill:{" + voucherId + "}:*:consumed";
        var options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (var cursor = stringRedisTemplate.execute((RedisCallback<Cursor<byte[]>>)
                connection -> connection.scan(options))) {
            while (true) {
                assert cursor != null;
                if (!cursor.hasNext()) break;
                stringRedisTemplate.delete(new String(cursor.next()));
            }
        }
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_SECKILL_SCRIPT, Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            LuaScriptUtil.load(SeckillConstant.LUA_ROLLBACK_SCRIPT, Long.class);

    @Override
    public String seckillVoucher(Long voucherId) {
        UserHolder.Context ctx = UserHolder.get();
        Long userId = ctx.userId();

        // 0. 风控检查（在扣减库存之前，risk-guard 内部已做异常兜底，不会向外抛异常）
        RiskDecision decision = riskGuardClient.check(RiskRequest.builder()
                .businessType("SECKILL")
                .userId(userId)
                .clientIp(ctx.clientIp())
                .userAgent(ctx.userAgent())
                .build());
        if (!decision.isPass()) {
            log.warn("风控拦截: userId={}, voucherId={}, reason={}", userId, voucherId, decision.getReason());
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

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
                throw new BusinessException(result == 1
                        ? MessageConstant.VOUCHER_INSUFFICIENT
                        : MessageConstant.REPEAT_ORDER);
            }

            // 3. MQ发送前先标记 PROCESSING，让用户及时感知这个订单正在处理
            stringRedisTemplate.opsForValue().set(idempotencyKey, SeckillConstant.STATUS_PROCESSING);

            // 4. 异步发送 MQ，失败回调自动回滚 Redis 库存
            log.info("开始异步发送MQ, userId={}, voucherId={}", userId, voucherId);
            seckillProducer.sendOrderAsync(new SeckillOrderMessage(userId, voucherId), stockKey, orderKey, idempotencyKey);

            // 返回处理中状态，前端轮询查询接口获取最终结果
            return MessageConstant.SECKILL_PROCESSING;
                
            // 业务异常（库存不足/重复下单）：Lua 未扣减库存，直接抛出不回滚
        } catch (BusinessException e) {
            throw e;

            // 系统异常：Lua 已扣减库存，回滚后抛出原始异常
        } catch (Exception e) {
            log.error("秒杀失败, 回滚Redis, voucherId={}, userId={}", voucherId, userId, e);
            stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Arrays.asList(stockKey, orderKey, idempotencyKey),
                    String.valueOf(userId), SeckillConstant.ROLLBACK_RESULT_FAIL, SeckillConstant.ROLLBACK_EXPIRE_SECONDS
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public void createSeckillOrder(Long userId, Long voucherId) {
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

        // 构建订单并写入数据库
        SeckillOrder seckillOrder = SeckillOrder.builder()
                .id(snowflakeIdGenerate.nextId())
                .userId(userId)
                .voucherId(voucherId)
                .payType(SeckillConstant.PAY_TYPE_BALANCE)
                .status(SeckillConstant.ORDER_STATUS_UNPAID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        save(seckillOrder);
    }

    @Override
    public String querySeckillStatus(Long voucherId) {
        Long userId = UserHolder.get().userId();
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