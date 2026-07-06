package com.flashdeal.service.api;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flashdeal.domain.Result;
import com.flashdeal.domain.SeckillOrder;

/**
 * 秒杀服务接口
 */
public interface SeckillService extends IService<SeckillOrder> {

    /**
     * 抢购秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 处理状态
     */
    Result<String> seckillVoucher(Long voucherId);

    /**
     * 创建订单
     *
     * @param seckillOrder 优惠券订单
     */
    void createSeckillOrder(SeckillOrder seckillOrder);

    /**
     * 查询秒杀状态
     *
     * @param voucherId 优惠券id
     * @return 状态
     */
    String  querySeckillStatus(Long voucherId);
}