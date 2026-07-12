local stockKey       = KEYS[1]
local orderKey       = KEYS[2]
local idempotencyKey = KEYS[3]
local userId         = ARGV[1]
local mode           = ARGV[2]  -- "DELETE" 清除状态 / "FAIL" 标记失败

-- 回补库存
redis.call('incr', stockKey)
-- 移除用户购买记录
redis.call('srem', orderKey, userId)

-- 处理幂等 key
if mode == "DELETE" then
    redis.call('del', idempotencyKey)
    return 1
end

-- FAIL 模式：标记失败并设置过期时间
local ttlSeconds = ARGV[3]
redis.call('set', idempotencyKey, "FAILED", "EX", tonumber(ttlSeconds))
return 1
