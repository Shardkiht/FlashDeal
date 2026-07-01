-- scripts/wrk_seckill_multi_user.lua
-- 多用户 Token 轮询压测脚本

local tokens = {}
local token_count = 0
local request_counter = 0

function init(args)
    math.randomseed(os.time())
    wrk.method = "POST"
    wrk.headers["Content-Type"] = "application/json"
    wrk.body = '{"id":1}'

    -- 每个线程独立加载 tokens
    local file = io.open("tokens.txt", "r")
    if not file then
        print("ERROR: tokens.txt not found!")
        return
    end

    for line in file:lines() do
        local user_id, phone, token = line:match("([^|]+)|([^|]+)|(.+)")
        if token then
            token_count = token_count + 1
            tokens[token_count] = {
                user_id = user_id,
                phone = phone,
                token = token
            }
        end
    end
    file:close()

    print("Thread loaded " .. token_count .. " tokens")
end

function request()
    if token_count == 0 then
        return wrk.format("POST", nil, {["Content-Type"] = "application/json"}, '{"id":1}')
    end

    local idx = math.random(1, token_count)
    local user = tokens[idx]

    request_counter = request_counter + 1

    local headers = {
        ["Content-Type"] = "application/json",
        ["authentication"] = user.token
    }

    return wrk.format("POST", nil, headers, '{"id":1}')
end

-- 处理响应（根据 Result.code 判断业务成功/失败）
function response(status, headers, body)
    local status_str = tostring(status)
    local body_str = tostring(body or "")

    -- 调试：打印前 10 个响应
    if request_counter <= 10 then
        print("DEBUG #" .. request_counter .. ": status=" .. status_str)
        if string.find(body_str, "\"code\":1") then
            print("  → SUCCESS (code=1)")
        elseif string.find(body_str, "\"code\":0") then
            if string.find(body_str, "系统繁忙") then
                print("  → FAIL: 系统繁忙 (code=0)")
            elseif string.find(body_str, "重复下单") then
                print("  → FAIL: 重复下单 (code=0)")
            elseif string.find(body_str, "库存不足") then
                print("  → FAIL: 库存不足 (code=0)")
            else
                print("  → FAIL: 其他错误 (code=0)")
            end
        else
            print("  → FAIL: 无法解析 code")
        end
    end
end

-- 压测结束统计
function done(summary, latency, requests)
    print("\n========================================")
    print("压测结果统计")
    print("========================================")
    print("总请求数:   " .. summary.requests)

    -- 打印 HTTP 状态码分布
    if summary.status_codes then
        print("\nHTTP 状态码分布:")
        for code, count in pairs(summary.status_codes) do
            print("  " .. code .. ": " .. count)
        end
    end

    print("\n延迟统计:")
    print("平均延迟:   " .. string.format("%.2f", latency.mean) .. "ms")
    print("P50 延迟:   " .. string.format("%.2f", latency:percentile(50)) .. "ms")
    print("P95 延迟:   " .. string.format("%.2f", latency:percentile(95)) .. "ms")
    print("P99 延迟:   " .. string.format("%.2f", latency:percentile(99)) .. "ms")
    print("\nQPS:        " .. string.format("%.2f", summary.requests / summary.duration) .. "/s")
    print("========================================")
    print("")
    print("业务逻辑验证:")
    print("  1. Redis 库存: redis-cli -a 865943 GET 'seckill:{1}:stock'")
    print("     期望值: 0（100张票全部卖出）")
    print("")
    print("  2. 已购买用户数: redis-cli -a 865943 SCARD 'seckill:{1}:order'")
    print("     期望值: 100（100个不同用户）")
    print("")
    print("  3. 数据库订单数: mysql -u shard -p865943 sky_take_out -e \"SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = 1;\"")
    print("     期望值: 100")
    print("========================================\n")
end