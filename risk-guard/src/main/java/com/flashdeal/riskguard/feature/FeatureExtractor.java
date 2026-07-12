package com.flashdeal.riskguard.feature;

import com.flashdeal.riskguard.api.RiskRequest;

/**
 * 特征提取顶层接口，按 businessType 分发
 */
public interface FeatureExtractor {

    /** 支持的业务类型 */
    String supportBusinessType();

    /**
     * 提取特征，返回 double[] 顺序必须与 DecisionTreeModel.FEATURE_NAMES 一致
     * @param request 风控请求
     * @return 6 维特征数组
     */
    double[] extract(RiskRequest request);
}
