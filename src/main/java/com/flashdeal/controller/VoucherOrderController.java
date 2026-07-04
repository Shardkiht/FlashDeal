package com.flashdeal.controller;

import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.common.utils.UserHolder;
import com.flashdeal.domain.Result;
import com.flashdeal.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券订单/秒杀控制器
 */
@RestController
@RequestMapping("/user/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券购买
     *
     * @param voucherId 优惠券id
     * @return 处理状态
     */
    @PostMapping("seckill/{id}")
    public Result<String> seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询秒杀订单处理状态
     *
     * @param voucherId 优惠券id
     * @return 状态：PROCESSING/SUCCESS/FAILED/UNKNOWN
     */
    @GetMapping("seckill/status/{voucherId}")
    public Result<String> queryOrderStatus(@PathVariable Long voucherId) {
        Long userId = UserHolder.getCurrentId();
        String status = stringRedisTemplate.opsForValue().get(RedisKeyConstant.getConsumedKey(userId, voucherId));
        return Result.success(status == null ? "UNKNOWN" : status);
    }
}