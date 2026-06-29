package com.flashdeal.utils;

/**
 * 当前登录用户上下文
 * 用于在登录拦截器中保存当前用户 ID，供后续秒杀等业务使用。
 */
public class UserHolder {

    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }
}