package com.flashdeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashdeal.domain.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券订单 Mapper 接口
 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
}