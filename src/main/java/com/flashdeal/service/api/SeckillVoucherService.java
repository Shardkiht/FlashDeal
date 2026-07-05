package com.flashdeal.service.api;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flashdeal.domain.SeckillVoucher;

/**
 * 秒杀优惠券服务接口
 */
public interface SeckillVoucherService extends IService<SeckillVoucher> {

    /**
     * 添加秒杀优惠券
     *
     * @param seckillVoucher 秒杀优惠券信息
     */
    void addSeckillVoucher(SeckillVoucher seckillVoucher);
}