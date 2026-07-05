local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId   = ARGV[1]

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey) or 0) <= 0) then
    return 1
end

if(redis.call('sadd', orderKey, userId) == 0) then
    return 2  -- sadd 返回0，说明这个用户已经在集合里了，判定重复下单
end

redis.call('decr', stockKey)
return 0
