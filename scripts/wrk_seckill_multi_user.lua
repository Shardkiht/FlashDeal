-- scripts/wrk_seckill_multi_user.lua
-- 多用户 Token 轮询压测脚本（静默版）

local tokens = {}
local token_count = 0
local loaded_once = false

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

-- 压测结束统计（简洁版）
function done(summary, latency, requests)
    -- 手动计算 QPS，避免 summary.duration 为 0
    local qps = 0
    if summary.duration and summary.duration > 0 then
        qps = summary.requests / summary.duration
    end

    print("\n========================================")
    print("总请求: " .. summary.requests .. " | QPS: " .. string.format("%.0f", qps))
    print("延迟: avg=" .. string.format("%.2f", latency.mean) ..
          "ms p50=" .. string.format("%.2f", latency:percentile(50)) ..
          "ms p99=" .. string.format("%.2f", latency:percentile(99)) .. "ms")
    print("========================================\n")
end