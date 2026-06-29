package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.common.constant.RedisKeyConstant;
import com.flashdeal.domain.SeckillVoucher;
import com.flashdeal.domain.Voucher;
import com.flashdeal.mapper.VoucherMapper;
import com.flashdeal.service.ISeckillVoucherService;
import com.flashdeal.service.IVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券服务实现类
 */
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());

        // 秒杀优惠券的库存单独保存
        seckillVoucherService.save(seckillVoucher);
        // 库存同步到 Redis
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.getSeckillVoucherStockKey(voucher.getId()),
                voucher.getStock().toString());
    }
}