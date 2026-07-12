package com.flashdeal.riskguard.model;

/**
 * 风控算法接口（支持换算法扩展）
 */
public interface RiskModel {
    /** 返回类别：0=正常，1=可疑，2=羊毛党 */
    int predict(double[] features);

    /** 返回各类别概率（可选，用于更细粒度的日志） */
    double[] predictProba(double[] features);
}
