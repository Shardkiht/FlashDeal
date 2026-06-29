package com.flashdeal.controller;

import com.flashdeal.domain.Result;
import com.flashdeal.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

/**
 * 优惠券订单/秒杀控制器
 */
@RestController
@RequestMapping("/user/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {

    private final IVoucherOrderService voucherOrderService;
    private final RedissonClient redissonClient;

    private static final String LIMITER_KEY = "seckill:limiter";

    @PostConstruct
    public void initRateLimiter() {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_KEY);
        limiter.trySetRate(RateType.OVERALL, 30000, 1, RateIntervalUnit.SECONDS);
    }

    /**
     * 秒杀优惠券购买
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        // 限流：每秒 30000 个请求
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_KEY);
        if (!limiter.tryAcquire()) {
            return Result.error("当前系统繁忙，请稍后重试");
        }
        return voucherOrderService.seckillVoucher(voucherId);
    }
}