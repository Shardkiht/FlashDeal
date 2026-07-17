package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.domain.SeckillVoucher;
import com.flashdeal.mapper.SeckillVoucherMapper;
import com.flashdeal.service.api.SeckillVoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀优惠券服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements SeckillVoucherService {

    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public void addSeckillVoucher(SeckillVoucher seckillVoucher) {
        save(seckillVoucher);
        Integer stock = seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock();
        if (seckillVoucher.getInitialStock() == null) {
            seckillVoucher.setInitialStock(stock);
            updateById(seckillVoucher);
        }
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.getSeckillStockKey(seckillVoucher.getId()), stock.toString());
    }
}