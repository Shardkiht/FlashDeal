package com.flashdeal.common.utils;

/**
 * 当前登录用户上下文
 * 用于在登录拦截器中保存当前用户 ID、客户端 IP、User-Agent，供后续业务使用。
 */
public class UserHolder {

    private static final ThreadLocal<Context> threadLocal = new ThreadLocal<>();

    public static void set(Context context) {
        threadLocal.set(context);
    }

    public static Context get() {
        return threadLocal.get();
    }

    public static void remove() {
        threadLocal.remove();
    }

    public record Context(Long userId, String clientIp, String userAgent) {
    }
}
