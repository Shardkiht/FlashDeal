package com.flashdeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashdeal.domain.Voucher;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券 Mapper 接口
 */
@Mapper
public interface VoucherMapper extends BaseMapper<Voucher> {
}