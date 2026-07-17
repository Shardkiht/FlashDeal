#!/bin/bash
# scripts/diversify_user_profiles.sh
# 读取 register_test_users.sh 生成的用户列表，按 userId % 100 < 5 分组：
#   - 羊毛党组（5%）：不处理，保持注册时的原始状态
#   - 正常用户组（95%）：把 risk:regtime 往前改、risk:orderCount 设为随机值，
#     区间和 BehaviorSimulator.java 训练时使用的区间保持一致。
#
# 用法: ./scripts/diversify_user_profiles.sh
# 依赖: 先运行 register_test_users.sh 生成 risk_tokens.txt

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/risk_tokens.txt"

REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
REDIS_PASS="${REDIS_PASS:-}"

if [ ! -f "$TOKEN_FILE" ]; then
    echo "错误: 找不到 $TOKEN_FILE，请先运行 register_test_users.sh"
    exit 1
fi

REDIS_CLI="podman exec -i $REDIS_CONTAINER redis-cli"
if [ -n "$REDIS_PASS" ]; then
    REDIS_CLI="$REDIS_CLI -a $REDIS_PASS"
fi

NOW_MS=$(date +%s%3N)
TOTAL=$(wc -l < "$TOKEN_FILE")
NORMAL_COUNT=0
BOT_COUNT=0

echo "========================================"
echo " 给测试用户制造画像区分度"
echo " 用户总数: $TOTAL"
echo "========================================"

{
while IFS='|' read -r userId phone token; do
    [ -z "$userId" ] && continue

    group=$((userId % 100))

    if [ "$group" -lt 5 ]; then
        BOT_COUNT=$((BOT_COUNT + 1))
        continue
    fi

    NORMAL_COUNT=$((NORMAL_COUNT + 1))
    daysAgo=$((30 + RANDOM % 970))
    regTimeMs=$((NOW_MS - daysAgo * 86400000))
    orderCount=$((RANDOM % 51))

    echo "SET risk:regtime:$userId $regTimeMs"
    echo "SET risk:orderCount:$userId $orderCount"
done < "$TOKEN_FILE"
} | $REDIS_CLI --pipe

echo "✓ 完成"
echo "正常用户组: $NORMAL_COUNT | 羊毛党组(未处理): $BOT_COUNT"
echo "========================================"
