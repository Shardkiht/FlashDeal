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
        // 保存秒杀优惠券
        save(seckillVoucher);

        // 库存同步到 Redis
        Integer stock = seckillVoucher.getStock();
        if (stock == null) {
            log.warn("秒杀券库存为空，默认设为 0, voucherId={}", seckillVoucher.getId());
            stock = 0;
        }
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.getSeckillStockKey(seckillVoucher.getId()),
                stock.toString());
    }
}