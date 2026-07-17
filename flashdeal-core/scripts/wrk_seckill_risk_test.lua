-- scripts/wrk_seckill_risk_test.lua
-- 风控压测脚本：按 userId % 100 < 5 分组，分别配不同IP池和UA

local tokens = {}
local token_count = 0
local threads = {}
local preq = {}
local preq_count = 0

-- 正常用户组：分散IP + 常见浏览器UA
local normal_ips = {
    "192.168.10.11", "192.168.20.34", "10.10.5.62", "172.16.8.201",
    "192.168.33.7", "10.20.44.19", "172.16.55.100", "192.168.99.2"
}
local normal_uas = {
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
}

-- 羊毛党组：集中在同一/24网段 + 可疑UA
local bot_ip_prefix = "10.0.0."
local bot_uas = {
    "HeadlessChrome/120.0.0.0",
    "Mozilla/5.0 (compatible; PhantomJS)"
}

function setup(thread)
    thread:set("rate_limited", 0)
    thread:set("stock_empty", 0)
    thread:set("repeat_order", 0)
    thread:set("success", 0)
    thread:set("risk_blocked", 0)
    table.insert(threads, thread)
end

function init(args)
    math.randomseed(os.time() + math.random(1, 10000))

    local file_paths = { "flashdeal-core/scripts/risk_tokens.txt", "scripts/risk_tokens.txt", "risk_tokens.txt", "./risk_tokens.txt" }
    local file = nil
    for _, path in ipairs(file_paths) do
        file = io.open(path, "r")
        if file then break end
    end
    if not file then
        print("ERROR: risk_tokens.txt not found")
        return
    end

    for line in file:lines() do
        local user_id, phone, token = line:match("([^|]+)|([^|]+)|(.+)")
        if token then
            token_count = token_count + 1
            tokens[token_count] = token

            local uid_num = tonumber(user_id)
            local is_bot = uid_num and (uid_num % 100 < 5)

            local ip, ua
            if is_bot then
                ip = bot_ip_prefix .. tostring(math.random(1, 254))
                ua = bot_uas[math.random(1, #bot_uas)]
            else
                ip = normal_ips[math.random(1, #normal_ips)]
                ua = normal_uas[math.random(1, #normal_uas)]
            end

            local hdrs = {
                ["Content-Type"] = "application/json",
                ["authentication"] = token,
                ["X-Forwarded-For"] = ip,
                ["User-Agent"] = ua
            }
            preq[token_count] = wrk.format("POST", "/user/seckill/1", hdrs, nil)
        end
    end
    file:close()
    print("Loaded " .. token_count .. " tokens")
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
    if body_str == "" then return end

    if string.find(body_str, '"code":1') then
        t:set("success", t:get("success") + 1)
    else
        if string.find(body_str, "RATE_LIMIT") or string.find(body_str, "系统繁忙") then
            t:set("rate_limited", t:get("rate_limited") + 1)
        elseif string.find(body_str, "操作过于频繁") then
            t:set("risk_blocked", t:get("risk_blocked") + 1)
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

    local t_limited, t_stock, t_repeat, t_success, t_risk = 0, 0, 0, 0, 0
    for _, thread in ipairs(threads) do
        t_limited = t_limited + (thread:get("rate_limited") or 0)
        t_stock = t_stock + (thread:get("stock_empty") or 0)
        t_repeat = t_repeat + (thread:get("repeat_order") or 0)
        t_success = t_success + (thread:get("success") or 0)
        t_risk = t_risk + (thread:get("risk_blocked") or 0)
    end

    print("\n========================================")
    print("总请求: " .. n .. " | QPS: " .. string.format("%.0f", qps))
    print("--- 业务分类 ---")
    print("限流拦截: " .. t_limited .. " (" .. string.format("%.2f", t_limited / n * 100) .. "%)")
    print("风控拦截: " .. t_risk .. " (" .. string.format("%.2f", t_risk / n * 100) .. "%)")
    print("库存不足: " .. t_stock .. " (" .. string.format("%.2f", t_stock / n * 100) .. "%)")
    print("重复下单: " .. t_repeat .. " (" .. string.format("%.2f", t_repeat / n * 100) .. "%)")
    print("成功/处理中: " .. t_success .. " (" .. string.format("%.2f", t_success / n * 100) .. "%)")
    print("延迟: avg=" .. string.format("%.2f", avg_ms) ..
            "ms p50=" .. string.format("%.2f", p50_ms) ..
            "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================\n")
end
