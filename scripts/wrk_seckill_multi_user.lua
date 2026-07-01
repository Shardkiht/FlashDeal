-- scripts/wrk_seckill_multi_user.lua
-- 多用户 Token 轮询压测脚本（静默版）

local tokens = {}
local token_count = 0
local RATE_LIMITED_FILE = "/tmp/wrk_rate_limited.txt"

function init(args)
    math.randomseed(os.time())
    wrk.method = "POST"
    wrk.headers["Content-Type"] = "application/json"

    -- 每个线程独立加载 tokens（支持多种路径）
    local file_paths = {
        "scripts/tokens.txt",
        "tokens.txt",
        "./tokens.txt"
    }

    local file = nil
    for _, path in ipairs(file_paths) do
        file = io.open(path, "r")
        if file then
            break
        end
    end

    if not file then
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

    -- 清空计数器文件（只由第一个线程执行，简单处理）
    if token_count > 0 and math.random(1, 100) <= 1 then
        local counter_file = io.open(RATE_LIMITED_FILE, "w")
        if counter_file then
            counter_file:write("")
            counter_file:close()
        end
    end
end

-- 生成请求（使用局部 headers，避免全局竞争）
function request()
    if token_count == 0 then
        return nil
    end

    -- 随机选择一个用户
    local idx = math.random(1, token_count)
    local user = tokens[idx]

    -- 构建局部 headers
    local req_headers = {
        ["Content-Type"] = "application/json",
        ["authentication"] = user.token
    }

    return wrk.format("POST", "/user/voucher-order/seckill/1", req_headers, nil)
end

-- 处理响应（统计限流）
function response(status, headers, body)
    local body_str = tostring(body or "")
    if string.find(body_str, "RATE_LIMITED") then
        -- 追加模式写入（多线程安全）
        local counter_file = io.open(RATE_LIMITED_FILE, "a")
        if counter_file then
            counter_file:write("1\n")
            counter_file:close()
        end
    end
end

-- 压测结束统计
function done(summary, latency, requests)
    -- 等待一小段时间确保所有写入完成
    os.execute("sleep 0.1")

    -- 读取并汇总限流计数
    local total_rate_limited = 0
    local counter_file = io.open(RATE_LIMITED_FILE, "r")
    if counter_file then
        for line in counter_file:lines() do
            total_rate_limited = total_rate_limited + tonumber(line)
        end
        counter_file:close()

        -- 清理临时文件
        os.remove(RATE_LIMITED_FILE)
    end

    -- 延迟单位转换：微秒 → 毫秒
    local avg_ms = latency.mean / 1000
    local p50_ms = latency:percentile(50) / 1000
    local p99_ms = latency:percentile(99) / 1000

    -- 计算 QPS（避免除零错误）
    local qps = 0
    if summary.duration and summary.duration > 0 then
        qps = summary.requests / summary.duration
    end

    print("\n========================================")
    print("总请求: " .. summary.requests .. " | QPS: " .. string.format("%.0f", qps))
    print("限流拦截: " .. total_rate_limited .. " (" .. string.format("%.2f", total_rate_limited / summary.requests * 100) .. "%)")
    print("延迟: avg=" .. string.format("%.2f", avg_ms) ..
          "ms p50=" .. string.format("%.2f", p50_ms) ..
          "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================\n")
end