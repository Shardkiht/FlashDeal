package com.flashdeal.riskguard.config;

import com.flashdeal.riskguard.api.RiskGuardClient;
import com.flashdeal.riskguard.model.DecisionTreeModel;
import com.flashdeal.riskguard.InProcessRiskGuardClient;
import com.flashdeal.riskguard.feature.FeatureExtractor;
import com.flashdeal.riskguard.feature.SeckillFeatureExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * risk-guard Spring 自动装配
 */
@Configuration
public class RiskGuardAutoConfiguration {

    @Value("${risk-guard.model-path:classpath:model.bin}")
    private String modelPath;

    @Bean
    public DecisionTreeModel decisionTreeModel() {
        try {
            String path = modelPath;
            if (path.startsWith("classpath:")) {
                path = path.substring("classpath:".length());
                // 尝试从文件系统加载
                java.io.File f = new java.io.File(path);
                if (!f.exists()) {
                    // 尝试从 classpath 加载
                    var url = getClass().getClassLoader().getResource(path);
                    if (url != null) {
                        path = url.getPath();
                    }
                }
            }
            return DecisionTreeModel.load(path);
        } catch (Exception e) {
            return null;
        }
    }

    @Bean
    public SeckillFeatureExtractor seckillFeatureExtractor(StringRedisTemplate redisTemplate) {
        return new SeckillFeatureExtractor(redisTemplate);
    }

    @Bean
    public RiskGuardClient riskGuardClient(
            DecisionTreeModel model,
            SeckillFeatureExtractor seckillFeatureExtractor) {
        // 将所有 FeatureExtractor 收集为 List，支持未来扩展新业务场景
        List<FeatureExtractor> extractors = List.of(seckillFeatureExtractor);
        return new InProcessRiskGuardClient(model, extractors);
    }
}
