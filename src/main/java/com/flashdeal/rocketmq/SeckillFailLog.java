package com.flashdeal.rocketmq;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀失败核查记录
 * 用于业务失败/重试耗尽场景下的结构化留痕，便于人工核查
 */
@Data
@AllArgsConstructor
public class SeckillFailLog {
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private String reason;
    private LocalDateTime failTime;
}