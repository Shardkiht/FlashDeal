package com.flashdeal.riskguard.api;

import com.flashdeal.riskguard.dto.RiskDecision;
import com.flashdeal.riskguard.dto.RiskRequest;

/**
 * 风控客户端接口（唯一暴露给 flashdeal-core 的入口）
 */
public interface RiskGuardClient {
    RiskDecision check(RiskRequest request);
}
