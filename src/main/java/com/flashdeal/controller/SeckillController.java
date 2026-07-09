package com.flashdeal.controller;

import com.flashdeal.domain.Result;
import com.flashdeal.service.api.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀控制器
 */
@RestController
@RequestMapping("/user/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 秒杀优惠券购买
     *
     * @param voucherId 优惠券id
     * @return 处理状态
     */
    @PostMapping("/{id}")
    public Result<String> seckillVoucher(@PathVariable("id") Long voucherId) {
        return Result.success(seckillService.seckillVoucher(voucherId));
    }

    /**
     * 查询秒杀订单处理状态
     *
     * @param voucherId 优惠券id
     * @return 状态：PROCESSING/SUCCESS/FAILED
     */
    @GetMapping("/status/{voucherId}")
    public Result<String> queryOrderStatus(@PathVariable Long voucherId) {
        return Result.success(seckillService.querySeckillStatus(voucherId));
    }
}
