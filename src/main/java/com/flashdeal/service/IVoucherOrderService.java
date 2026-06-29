package com.flashdeal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flashdeal.domain.Result;
import com.flashdeal.domain.VoucherOrder;

/**
 * 优惠券订单服务接口
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单
     *
     * @param voucherOrder 优惠券订单
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}