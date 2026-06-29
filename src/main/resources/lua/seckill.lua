local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId   = ARGV[1]

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey) or 0) <= 0) then
    return 1
end

-- 判断用户是否重复下单
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 扣减库存并记录用户
redis.call('decr', stockKey)
redis.call('sadd', orderKey, userId)
return 0
