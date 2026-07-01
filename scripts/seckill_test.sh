#!/bin/bash

# seckill_test.sh
# 一键秒杀压测脚本：自动获取多用户 Token 并执行 wrk 压测
# 用法: ./seckill_test.sh [用户数] [并发数] [持续时间] [优惠券ID]
# 示例: ./seckill_test.sh 100 50 10s 1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN_FILE="$SCRIPT_DIR/tokens.txt"
LUA_SCRIPT="$SCRIPT_DIR/wrk_seckill_multi_user.lua"

# 参数配置（可自定义）
USER_COUNT=${1:-100}      # 模拟用户数
CONCURRENCY=${2:-50}      # 并发连接数
DURATION=${3:-10s}        # 压测持续时间
VOUCHER_ID=${4:-1}        # 优惠券ID

# 线程数（根据 CPU 核心数自动调整，最多 8）
THREADS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
if [ "$THREADS" -gt 8 ]; then
    THREADS=8
fi

echo ""
echo "========================================"
echo " FlashDeal 秒杀压测"
echo "========================================"
echo "用户: $USER_COUNT | 并发: $CONCURRENCY | 时长: $DURATION | 券ID: $VOUCHER_ID"
echo "========================================"
echo ""

# Step 1: 获取 Token（静默模式）
printf "获取 %d 个 Token... " "$USER_COUNT"

> "$TOKEN_FILE"

for i in $(seq 1 "$USER_COUNT"); do
    phone="1380000$(printf '%04d' $i)"
    response=$(curl -s -X POST "$BASE_URL/user/login" \
        -H "Content-Type: application/json" \
        -d "{\"phone\": \"$phone\"}")

    token=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    user_id=$(echo "$response" | grep -o '"id":[0-9]*' | cut -d':' -f2)

    if [ -n "$token" ]; then
        echo "$user_id|$phone|$token" >> "$TOKEN_FILE"
    fi
done

echo "✓ 完成"
echo ""

# Step 2: 运行 wrk 压测
wrk -t"$THREADS" -c"$CONCURRENCY" -d"$DURATION" -s "$LUA_SCRIPT" "$BASE_URL/user/voucher-order/seckill/$VOUCHER_ID"

echo ""
echo "========================================"
echo "🎉 压测完成！"
echo "========================================"
echo ""
