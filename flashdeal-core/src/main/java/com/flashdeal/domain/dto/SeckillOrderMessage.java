package com.flashdeal.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 秒杀订单 MQ 消息体
 * 仅携带创建订单的最小必要信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下单用户 ID
     */
    private Long userId;

    /**
     * 秒杀优惠券 ID
     */
    private Long voucherId;
}
