-- scripts/wrk_single_user_repeat.lua
-- 单用户重复下单测试：测量拒绝响应的平均时间

local req = nil
local threads = {}

function setup(thread)
    thread:set("rejected", 0)
    thread:set("success", 0)
    thread:set("other", 0)
    table.insert(threads, thread)
end

function init(args)
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

    local line = file:read("*l")
    file:close()
    local user_id, phone, tk = line:match("([^|]+)|([^|]+)|(.+)")
    if not tk then
        print("ERROR: failed to parse token")
        return
    end
    print("Single user test, userId=" .. user_id)

    local hdrs = {
        ["Content-Type"] = "application/json",
        ["authentication"] = tk,
        ["X-Forwarded-For"] = "192.168.10.11",
        ["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    req = wrk.format("POST", "/user/seckill/1", hdrs, nil)
end

function request()
    return req
end

function response(status, headers, body)
    local t = wrk.thread
    local body_str = body and tostring(body) or ""
    if body_str == "" then return end

    if string.find(body_str, '"code":1') then
        t:set("success", t:get("success") + 1)
    elseif string.find(body_str, "不能重复下单") or string.find(body_str, "操作过于频繁") then
        t:set("rejected", t:get("rejected") + 1)
    else
        t:set("other", t:get("other") + 1)
    end
end

function done(summary, latency, requests)
    local avg_ms = latency.mean / 1000
    local p50_ms = latency:percentile(50) / 1000
    local p99_ms = latency:percentile(99) / 1000
    local n = summary.requests

    local t_rejected, t_success, t_other = 0, 0, 0
    for _, thread in ipairs(threads) do
        t_rejected = t_rejected + (thread:get("rejected") or 0)
        t_success = t_success + (thread:get("success") or 0)
        t_other = t_other + (thread:get("other") or 0)
    end

    print("\n========================================")
    print("单用户重复下单拒绝时间测试")
    print("总请求: " .. n)
    print("成功(首次): " .. t_success)
    print("拒绝(重复/风控): " .. t_rejected)
    print("其他: " .. t_other)
    print("延迟(全部): avg=" .. string.format("%.2f", avg_ms) ..
            "ms p50=" .. string.format("%.2f", p50_ms) ..
            "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================\n")
end
