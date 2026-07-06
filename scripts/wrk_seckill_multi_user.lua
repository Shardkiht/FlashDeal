-- scripts/wrk_seckill_multi_user.lua
-- 多用户 Token 轮询压测脚本

local tokens = {}
local token_count = 0
local threads = {}
local preq = {}
local preq_count = 0

-- setup 和 done 共享同一个 Lua 状态，局部变量 threads 两者均可访问
function setup(thread)
    thread:set("rate_limited", 0)
    thread:set("stock_empty", 0)
    thread:set("repeat_order", 0)
    thread:set("success", 0)
    table.insert(threads, thread)
end

function init(args)
    math.randomseed(os.time() + math.random(1, 10000))

    -- 加载 tokens
    local file_paths = { "scripts/tokens.txt", "tokens.txt", "./tokens.txt" }
    local file = nil
    for _, path in ipairs(file_paths) do
        file = io.open(path, "r")
        if file then
            break
        end
    end
    if not file then
        print("ERROR: tokens.txt not found")
        return
    end

    for line in file:lines() do
        local user_id, phone, token = line:match("([^|]+)|([^|]+)|(.+)")
        if token then
            token_count = token_count + 1
            tokens[token_count] = token
        end
    end
    file:close()
    print("Loaded " .. token_count .. " tokens")

    -- 预构建请求（wrk.format 会消费 headers，只调用一次）
    for i = 1, token_count do
        local hdrs = {
            ["Content-Type"] = "application/json",
            ["authentication"] = tokens[i]
        }
        preq[i] = wrk.format("POST", "/user/seckill/1", hdrs, nil)
    end
    preq_count = token_count
end

function request()
    if preq_count == 0 then
        return nil
    end
    local idx = math.random(1, preq_count)
    return preq[idx]
end

function response(status, headers, body)
    local t = wrk.thread
    if status == 429 then
        t:set("rate_limited", t:get("rate_limited") + 1)
        return
    end

    local body_str = body and tostring(body) or ""
    if body_str == "" then
        return
    end

    if string.find(body_str, '"code":1') then
        t:set("success", t:get("success") + 1)
    else
        if string.find(body_str, "RATE_LIMIT") or string.find(body_str, "系统繁忙") then
            t:set("rate_limited", t:get("rate_limited") + 1)
        elseif string.find(body_str, "已卖完") or string.find(body_str, "库存不足") then
            t:set("stock_empty", t:get("stock_empty") + 1)
        elseif string.find(body_str, "不能重复下单") then
            t:set("repeat_order", t:get("repeat_order") + 1)
        end
    end
end

function done(summary, latency, requests)
    local avg_ms = latency.mean / 1000
    local p50_ms = latency:percentile(50) / 1000
    local p99_ms = latency:percentile(99) / 1000
    local n = summary.requests

    local qps = 0
    if summary.duration and summary.duration > 0 then
        qps = n / (summary.duration / 1000000)
    end

    -- 汇总所有线程计数器
    local t_limited, t_stock, t_repeat, t_success = 0, 0, 0, 0
    for _, thread in ipairs(threads) do
        t_limited = t_limited + (thread:get("rate_limited") or 0)
        t_stock = t_stock + (thread:get("stock_empty") or 0)
        t_repeat = t_repeat + (thread:get("repeat_order") or 0)
        t_success = t_success + (thread:get("success") or 0)
    end

    print("\n========================================")
    print("总请求: " .. n .. " | QPS: " .. string.format("%.0f", qps))
    print("--- 业务分类 ---")
    print("限流拦截: " .. t_limited .. " (" .. string.format("%.2f", t_limited / n * 100) .. "%)")
    print("库存不足: " .. t_stock .. " (" .. string.format("%.2f", t_stock / n * 100) .. "%)")
    print("重复下单: " .. t_repeat .. " (" .. string.format("%.2f", t_repeat / n * 100) .. "%)")
    print("成功/处理中: " .. t_success .. " (" .. string.format("%.2f", t_success / n * 100) .. "%)")
    print("延迟: avg=" .. string.format("%.2f", avg_ms) ..
            "ms p50=" .. string.format("%.2f", p50_ms) ..
            "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================\n")
end