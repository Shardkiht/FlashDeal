#!/bin/bash
# scripts/single_user_test.sh
# 单用户秒杀压测脚本（自动读取 token）
# 用法: ./scripts/single_user_test.sh [并发数] [持续时间] [优惠券ID]
# 示例: ./scripts/single_user_test.sh 50 10s 1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/tokens.txt"

# 参数配置
CONCURRENCY=${1:-50}      # 并发连接数
DURATION=${2:-10s}        # 压测持续时间
VOUCHER_ID=${3:-1}        # 优惠券ID

# 检查 tokens.txt 是否存在
if [ ! -f "$TOKEN_FILE" ]; then
    echo "❌ 错误: tokens.txt 不存在"
    echo "请先运行: ./scripts/seckill_test.sh 100"
    exit 1
fi

# 读取第一个用户的 token
TOKEN=$(head -1 "$TOKEN_FILE" | cut -d'|' -f3)
USER_ID=$(head -1 "$TOKEN_FILE" | cut -d'|' -f1)
PHONE=$(head -1 "$TOKEN_FILE" | cut -d'|' -f2)

if [ -z "$TOKEN" ]; then
    echo "❌ 错误: tokens.txt 为空或格式错误"
    exit 1
fi

echo ""
echo "========================================"
echo " FlashDeal 单用户压测"
echo "========================================"
echo "用户: $USER_ID ($PHONE)"
echo "并发: $CONCURRENCY | 时长: $DURATION | 券ID: $VOUCHER_ID"
echo "========================================"
echo ""

# 创建临时 Lua 脚本
LUA_SCRIPT=$(mktemp /tmp/single_user_XXXXXX.lua)

cat > "$LUA_SCRIPT" << EOF
wrk.method = "POST"
wrk.path = "/user/voucher-order/seckill/$VOUCHER_ID"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["authentication"] = "$TOKEN"

function request()
    return wrk.format("POST", "/user/voucher-order/seckill/$VOUCHER_ID", wrk.headers, nil)
end

function done(summary, latency, requests)
    -- 延迟单位转换：微秒 → 毫秒
    local avg_ms = latency.mean / 1000
    local p50_ms = latency:percentile(50) / 1000
    local p99_ms = latency:percentile(99) / 1000

    print("\\n========================================")
    print("总请求: " .. summary.requests)
    print("延迟: avg=" .. string.format("%.2f", avg_ms) ..
          "ms p50=" .. string.format("%.2f", p50_ms) ..
          "ms p99=" .. string.format("%.2f", p99_ms) .. "ms")
    print("========================================\\n")
    print("注意: QPS 请查看上方 wrk 输出的 Requests/sec 字段")
    print("========================================\\n")
end
EOF

# 执行压测
wrk -t1 -c$CONCURRENCY -d$DURATION -s "$LUA_SCRIPT" http://localhost:8080/user/voucher-order/seckill/$VOUCHER_ID

# 清理临时文件
rm -f "$LUA_SCRIPT"

echo ""
echo "========================================"
echo "✅ 压测完成"
echo "========================================"
echo ""
