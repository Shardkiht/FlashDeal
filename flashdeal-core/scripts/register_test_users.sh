#!/bin/bash
# scripts/register_test_users.sh
# 通过真实调用 /user/login 注册一批新用户，触发自动注册分支写入 risk:regtime。
# 手机号用全新号段(137)，避免和历史用户冲突。重复运行安全。
#
# 用法: ./scripts/register_test_users.sh [用户数]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN_FILE="$SCRIPT_DIR/risk_tokens.txt"

USER_COUNT=${1:-5000}

echo "========================================"
echo " 注册风控测试用户（手机号段 137）"
echo " 用户数: $USER_COUNT"
echo "========================================"

> "$TOKEN_FILE"

fetch_token() {
    local i=$1
    local phone="1370000$(printf '%04d' "$i")"
    local response
    response=$(curl -s -X POST "$BASE_URL/user/login" \
        -H "Content-Type: application/json" \
        -d "{\"phone\": \"$phone\"}")

    local token
    token=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    local user_id
    user_id=$(echo "$response" | grep -o '"id":[0-9]*' | cut -d':' -f2)

    if [ -n "$token" ]; then
        echo "$user_id|$phone|$token"
    fi
}

export -f fetch_token
export BASE_URL

printf "注册 %d 个用户... " "$USER_COUNT"
seq 1 "$USER_COUNT" | xargs -P 50 -I {} bash -c 'fetch_token "$@"' _ {} >> "$TOKEN_FILE"

REGISTERED=$(wc -l < "$TOKEN_FILE")
echo "✓ 完成，成功注册/登录 $REGISTERED 个用户"
echo "已保存到 $TOKEN_FILE（格式：userId|phone|token）"
echo "========================================"
echo "下一步：运行 diversify_user_profiles.sh 给这批用户制造画像区分度"
echo "========================================"
