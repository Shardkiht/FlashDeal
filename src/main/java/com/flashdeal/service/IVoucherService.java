package com.flashdeal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flashdeal.domain.Voucher;

/**
 * 优惠券服务接口
 */
public interface IVoucherService extends IService<Voucher> {

    void addSeckillVoucher(Voucher voucher);
}