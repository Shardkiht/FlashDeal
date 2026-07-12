package com.flashdeal.riskguard.api;

/**
 * 风控客户端接口（唯一暴露给 flashdeal-core 的入口）
 */
public interface RiskGuardClient {
    RiskDecision check(RiskRequest request);
}
