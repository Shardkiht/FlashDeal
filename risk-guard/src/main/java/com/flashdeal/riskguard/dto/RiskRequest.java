package com.flashdeal.riskguard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 风控请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskRequest {
    /** 业务类型：SECKILL / REGISTER / COMMENT 等 */
    private String businessType;
    /** 当前请求用户 ID */
    private Long userId;
    /** 客户端 IP */
    private String clientIp;
    /** 请求头 User-Agent */
    private String userAgent;
    /** 可选：调用方已算好的特征，未提供的由 risk-guard 内部补全 */
    private Map<String, Double> features;
}
