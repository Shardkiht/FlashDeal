#!/bin/bash
# scripts/single_user_test.sh
# 单用户秒杀压测脚本（自动读取 token）
# 用法: ./scripts/single_user_test.sh [并发数] [持续时间] [优惠券ID]
# 示例: ./scripts/single_user_test.sh 50 10s 1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/tokens.txt"

CONCURRENCY=${1:-50}
DURATION=${2:-10s}
VOUCHER_ID=${3:-1}

if [ ! -f "$TOKEN_FILE" ]; then
    echo "❌ 错误: tokens.txt 不存在"
    echo "请先运行: ./scripts/seckill_test.sh 100"
    exit 1
fi

TOKEN=$(head -1 "$TOKEN_FILE" | cut -d'|' -f3)
USER_ID=$(head -1 "$TOKEN_FILE" | cut -d'|' -f1)
PHONE=$(head -1 "$TOKEN_FILE" | cut -d'|' -f2)

if [ -z "$TOKEN" ]; then
    echo "❌ 错误: tokens.txt 为空或格式错误"
    exit 1
fi

echo "========================================"
echo " FlashDeal 单用户压测"
echo "========================================"
echo "用户: $USER_ID ($PHONE)"
echo "并发: $CONCURRENCY | 时长: $DURATION | 券ID: $VOUCHER_ID"
echo "========================================"

LUA_SCRIPT=$(mktemp /tmp/single_user_XXXXXX.lua)

cat > "$LUA_SCRIPT" << EOF
wrk.method = "POST"
wrk.path = "/user/seckill/$VOUCHER_ID"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["authentication"] = "$TOKEN"

local threads = {}

function setup(thread)
    thread:set("blocked", 0)
    thread:set("stock", 0)
    thread:set("repeat_o", 0)
    thread:set("success", 0)
    thread:set("other", 0)
    thread:set("empty_body", 0)
    thread:set("sample", "")
    table.insert(threads, thread)
end

-- wrk.format 会消费 wrk.headers，所以必须在 init() 中预构建请求
function init(args)
    req = wrk.format("POST", "/user/seckill/$VOUCHER_ID", wrk.headers, nil)
end

function request()
    return req
end

function response(status, headers, body)
    if status == 429 then
        blocked = (blocked or 0) + 1
        return
    end

    local body_str = body and tostring(body) or ""

    -- 记录前3个非429响应的body用于调试
    local n = (other or 0) + (success or 0) + (stock or 0) + (repeat_o or 0) + (empty_body or 0)
    if n < 3 and sample == "" then
        sample = "status=" .. status .. " body_len=" .. #body_str .. " body=" .. string.sub(body_str, 1, 200)
    end

    if body_str == "" then
        empty_body = (empty_body or 0) + 1
        return
    end

    if string.find(body_str, '"code":1') or string.find(body_str, '"code": 1') then
        success = (success or 0) + 1
    else
        if string.find(body_str, "RATE_LIMIT") or string.find(body_str, "系统繁忙") then
            blocked = (blocked or 0) + 1
        elseif string.find(body_str, "已卖完") or string.find(body_str, "库存不足") then
            stock = (stock or 0) + 1
        elseif string.find(body_str, "不能重复下单") then
            repeat_o = (repeat_o or 0) + 1
        else
            other = (other or 0) + 1
        end
    end
end

function done(summary, latency, requests)
    local avg_ms = latency.mean / 1000
    local p50_ms = latency:percentile(50) / 1000
    local p99_ms = latency:percentile(99) / 1000
    local n = summary.requests

    local t_blocked, t_stock, t_repeat, t_success, t_other, t_empty = 0, 0, 0, 0, 0, 0
    local first_sample = ""
    for i, thread in ipairs(threads) do
        t_blocked  = t_blocked + thread:get("blocked")
        t_stock    = t_stock + thread:get("stock")
        t_repeat   = t_repeat + thread:get("repeat_o")
        t_success  = t_success + thread:get("success")
        t_other    = t_other + thread:get("other")
        t_empty    = t_empty + thread:get("empty_body")
        if first_sample == "" then
            first_sample = thread:get("sample") or ""
        end
    end

    print("========================================")
    print("总请求: " .. n)
    print("--- 业务分类 ---")
    print("限流拦截: " .. t_blocked .. " (" .. string.format("%.2f", t_blocked / n * 100) .. "%)")
    print("库存不足: " .. t_stock .. " (" .. string.format("%.2f", t_stock / n * 100) .. "%)")
    print("重复下单: " .. t_repeat .. " (" .. string.format("%.2f", t_repeat / n * 100) .. "%)")
    print("成功/处理中: " .. t_success .. " (" .. string.format("%.2f", t_success / n * 100) .. "%)")
    print("未匹配: " .. t_other .. " | 空body: " .. t_empty)
    if first_sample ~= "" then
        print("--- 响应样本 ---")
        print(first_sample)
    end
    print("延迟: avg=" .. string.format("%.2f", avg_ms) ..
          "ms p50=" .. string.format("%.2f", p50_ms) ..
          "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================")
end
EOF

wrk -t1 -c"$CONCURRENCY" -d"$DURATION" -s "$LUA_SCRIPT" "http://localhost:8080/user/seckill/$VOUCHER_ID"

rm -f "$LUA_SCRIPT"

echo "========================================"
echo "✅ 压测完成"
echo "========================================"
