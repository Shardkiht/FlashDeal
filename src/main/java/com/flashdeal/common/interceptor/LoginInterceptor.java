package com.flashdeal.common.interceptor;

import com.flashdeal.common.constant.JwtClaimsConstant;
import com.flashdeal.common.properties.JwtProperties;
import com.flashdeal.common.utils.JwtUtil;
import com.flashdeal.common.utils.UserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户登录 JWT 校验拦截器
 */
@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是 Controller 的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 登录接口直接放行
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/user/login")) {
            return true;
        }

        // 2. 从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 3. 令牌为空直接拦截
        if (token == null || token.isBlank()) {
            log.warn("请求缺少token, URL: {}", requestURI);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"用户未登录\",\"data\":null}");
            return false;
        }

        // 4. 校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);
            UserHolder.setCurrentId(userId);
            return true;
        } catch (Exception ex) {
            log.error("JWT解析失败: {}", ex.getMessage(), ex);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"用户未登录\",\"data\":null}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeCurrentId();
    }
}