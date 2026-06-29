package com.flashdeal.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Lua 脚本加载工具（新增）
 * 统一管理 Redis Lua 脚本的加载与类型声明。
 */
public class LuaScriptUtil {

    /**
     * 加载类路径下的 Lua 脚本
     *
     * @param path       Lua 脚本路径，如 "lua/seckill.lua"
     * @param resultType 脚本返回值类型
     * @return DefaultRedisScript
     */
    public static <T> DefaultRedisScript<T> load(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
