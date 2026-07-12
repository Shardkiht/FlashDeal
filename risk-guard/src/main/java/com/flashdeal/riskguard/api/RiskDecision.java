package com.flashdeal.riskguard.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控决策结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecision {
    /** true=放行，false=拦截 */
    private boolean pass;
    /** NORMAL / SUSPICIOUS / BLOCKED */
    private String riskLevel;
    /** 判定依据说明，便于日志排查 */
    private String reason;

    public static RiskDecision pass(String reason) {
        return RiskDecision.builder().pass(true).riskLevel("NORMAL").reason(reason).build();
    }

    public static RiskDecision block(String riskLevel, String reason) {
        return RiskDecision.builder().pass(false).riskLevel(riskLevel).reason(reason).build();
    }
}
