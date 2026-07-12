package com.flashdeal.controller;

import com.flashdeal.domain.Result;
import com.flashdeal.domain.SeckillVoucher;
import com.flashdeal.service.api.SeckillVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final SeckillVoucherService seckillVoucherService;

    /**
     * 添加秒杀优惠券
     *
     * @param seckillVoucher 秒杀优惠券信息
     * @return 成功结果
     */
    @PostMapping("/voucher/seckill")
    public Result<String> addSeckillVoucher(@RequestBody SeckillVoucher seckillVoucher) {
        seckillVoucherService.addSeckillVoucher(seckillVoucher);
        return Result.success("添加成功");
    }
}