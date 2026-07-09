package com.flashdeal.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdeal.common.constant.JwtClaimsConstant;
import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.common.properties.JwtProperties;
import com.flashdeal.common.utils.JwtUtil;
import com.flashdeal.common.utils.UserHolder;
import com.flashdeal.domain.Result;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户登录 JWT 校验拦截器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 判断当前拦截到的是 Controller 的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 2. 令牌为空直接拦截
        if (token == null || token.isBlank()) {
            log.warn("请求缺少token, URL: {}", request.getRequestURI());
            writeErrorResponse(response);
            return false;
        }

        // 3. 校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);
            UserHolder.setCurrentId(userId);
            return true;
        } catch (Exception ex) {
            log.error("JWT解析失败: {}", ex.getMessage(), ex);
            writeErrorResponse(response);
            return false;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        UserHolder.removeCurrentId();
    }

    private void writeErrorResponse(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        response.setContentType(MessageConstant.CONTENT_TYPE_JSON);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(MessageConstant.USER_NOT_LOGIN)
        ));
    }
}