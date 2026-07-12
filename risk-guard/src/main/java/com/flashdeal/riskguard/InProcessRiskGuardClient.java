package com.flashdeal.riskguard;

import com.flashdeal.riskguard.api.RiskDecision;
import com.flashdeal.riskguard.api.RiskGuardClient;
import com.flashdeal.riskguard.api.RiskRequest;
import com.flashdeal.riskguard.feature.FeatureExtractor;
import com.flashdeal.riskguard.model.DecisionTreeModel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RiskGuardClient 的进程内实现
 */
@Slf4j
public class InProcessRiskGuardClient implements RiskGuardClient {

    private final DecisionTreeModel model;
    private final boolean modelAvailable;
    private final Map<String, FeatureExtractor> extractorMap;

    public InProcessRiskGuardClient(DecisionTreeModel model, List<FeatureExtractor> extractors) {
        this.model = model;
        this.modelAvailable = (model != null);
        this.extractorMap = extractors.stream()
                .collect(Collectors.toMap(FeatureExtractor::supportBusinessType, Function.identity()));

        if (!modelAvailable) {
            log.error("DecisionTreeModel 加载失败，风控将降级放行");
        }
    }

    @Override
    public RiskDecision check(RiskRequest request) {
        try {
            if (!modelAvailable) {
                return RiskDecision.pass("风控降级放行：模型未加载");
            }

            String businessType = request.getBusinessType();
            FeatureExtractor extractor = extractorMap.get(businessType);
            if (extractor == null) {
                log.warn("未知业务类型: {}, 放行", businessType);
                return RiskDecision.pass("未知业务类型，放行");
            }

            // 提取特征
            double[] features = extractor.extract(request);

            // 推理
            int predicted = model.predict(features);

            if (predicted == 2) {
                // 羊毛党 → 拦截
                String reason = buildReason(features, predicted);
                log.warn("风控拦截: userId={}, businessType={}, {}",
                        request.getUserId(), businessType, reason);
                return RiskDecision.block("BLOCKED", reason);
            }

            return RiskDecision.pass("predict=" + predicted);

        } catch (Exception e) {
            log.error("风控异常，降级放行: businessType={}, userId={}",
                    request.getBusinessType(), request.getUserId(), e);
            return RiskDecision.pass("风控异常降级放行");
        }
    }

    /** 构建拦截原因描述（便于日志排查） */
    private String buildReason(double[] features, int predicted) {
        StringBuilder sb = new StringBuilder();
        sb.append("predicted=").append(predicted);
        sb.append(", features=[");
        String[] names = DecisionTreeModel.FEATURE_NAMES;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(names[i]).append("=").append(String.format("%.2f", features[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
