package com.flashdeal.common.interceptor;

import com.flashdeal.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.PostConstruct;

/**
 * 限流拦截器
 * 基于 Redisson 令牌桶算法，全局限流 3000 req/s
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final String LIMITER_KEY = "seckill:limiter";

    @PostConstruct
    public void initRateLimiter() {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_KEY);
        limiter.trySetRate(RateType.OVERALL, 3000, 1, RateIntervalUnit.SECONDS);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_KEY);
        if (!limiter.tryAcquire()) {
            log.warn("请求被限流拦截, URI: {}", request.getRequestURI());
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error("当前系统繁忙，请稍后重试")
            ));
            return false;
        }
        return true;
    }
}
