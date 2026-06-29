package com.flashdeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flashdeal.domain.SeckillVoucher;
import com.flashdeal.mapper.SeckillVoucherMapper;
import com.flashdeal.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * 秒杀优惠券服务实现类
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
}