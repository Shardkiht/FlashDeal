package com.flashdeal.controller;

import com.flashdeal.domain.Result;
import com.flashdeal.domain.Voucher;
import com.flashdeal.service.IVoucherService;
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

    private final IVoucherService voucherService;

    /**
     * 添加秒杀优惠券
     *
     * @param voucher 优惠券信息
     * @return 成功结果
     */
    @PostMapping("/voucher/seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.success();
    }
}