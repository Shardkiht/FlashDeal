package com.flashdeal.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 * 用户端 JWT 配置属性。
 */
@Component
@ConfigurationProperties(prefix = "flashdeal.jwt")
@Data
public class JwtProperties {

    /**
     * 用户端生成 jwt 令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;
}