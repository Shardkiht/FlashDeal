#!/bin/bash
# scripts/generate_test_users_orders.sh
# 随机生成一批用户和历史订单数据，制造"正常用户"和"羊毛党"两种不同特征画像
# 生成的是真实 DB 数据（真实 create_time、真实 voucher_order 行），
# 生成完之后配合 migrate_legacy_users_to_redis.sh 同步到 Redis，
# 这样 DB 和 Redis 数据是一致的，不是两边各编一套。
#
# 用法: ./scripts/generate_test_users_orders.sh [用户数] [羊毛党比例]
# 示例: ./scripts/generate_test_users_orders.sh 5000 0.05

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

USER_COUNT=${1:-5000}
BOT_RATIO=${2:-0.05}

APP_YAML="$SCRIPT_DIR/../src/main/resources/application-dev.yaml"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-flashdeal}"
DB_USER="${DB_USER:-}"
DB_PASS="${DB_PASS:-}"

if [ -f "$APP_YAML" ]; then
    DB_NAME=$(grep -o 'jdbc:mysql://[^?]*' "$APP_YAML" | sed 's/.*\/\(.*\)/\1/')
    DB_USER=$(awk '/datasource:/{flag=1} flag && /username:/{print $2; exit}' "$APP_YAML" | tr -d ' ')
    DB_PASS=$(awk '/datasource:/{flag=1} flag && /password:/{print $2; exit}' "$APP_YAML" | tr -d ' ')
fi

# 历史订单挂靠的 voucherId：用一个专门的"历史占位券"，不要用当前正在测试的秒杀券 ID，
# 避免生成的历史订单和真实压测的库存/幂等逻辑产生干扰。
# 需要确保 tb_seckill_voucher 表里存在这个 id（脚本会先检查，不存在就跳过订单生成、只提示）
HISTORY_VOUCHER_ID=${HISTORY_VOUCHER_ID:-999}

echo "========================================"
echo " 生成随机用户 + 历史订单测试数据"
echo " 用户数: $USER_COUNT | 羊毛党比例: $BOT_RATIO"
echo " 数据库: $DB_NAME@$DB_HOST:$DB_PORT"
echo "========================================"

MYSQL_CMD="mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME"

# 检查历史占位券是否存在，不存在就自动建一个（stock 设 0，不影响真实秒杀库存判断）
VOUCHER_EXISTS=$($MYSQL_CMD -N -B -e "SELECT COUNT(*) FROM tb_seckill_voucher WHERE id = $HISTORY_VOUCHER_ID")
if [ "$VOUCHER_EXISTS" -eq 0 ]; then
    echo "历史占位券(id=$HISTORY_VOUCHER_ID)不存在，自动创建..."
    $MYSQL_CMD -e "INSERT INTO tb_seckill_voucher (id, stock, create_time, begin_time, end_time, update_time)
                   VALUES ($HISTORY_VOUCHER_ID, 0, NOW(), '2020-01-01 00:00:00', '2020-01-02 00:00:00', NOW())
                   ON DUPLICATE KEY UPDATE id=id"
fi

SQL_FILE="/tmp/gen_users_orders_$$.sql"
echo "USE \`$DB_NAME\`;" > "$SQL_FILE"

echo "生成 SQL..."

# ---------- 1. 生成用户（带区分度的注册时间）----------
echo "INSERT INTO user (phone, create_time) VALUES" >> "$SQL_FILE"

USER_TYPE_FILE="/tmp/user_types_$$.txt"   # 记录每个用户是 normal 还是 bot，供第2步生成订单时使用
> "$USER_TYPE_FILE"

for i in $(seq 1 "$USER_COUNT"); do
    phone="1390000$(printf '%04d' $i)"

    rand=$((RANDOM % 1000))
    threshold=$(awk -v r="$BOT_RATIO" 'BEGIN{printf "%d", r*1000}')

    if [ "$rand" -lt "$threshold" ]; then
        # 羊毛党画像：注册 0~7 天前
        daysAgo=$((RANDOM % 7))
        echo "bot" >> "$USER_TYPE_FILE"
    else
        # 正常用户画像：注册 30~1000 天前
        daysAgo=$((30 + RANDOM % 970))
        echo "normal" >> "$USER_TYPE_FILE"
    fi

    createTime="DATE_SUB(NOW(), INTERVAL $daysAgo DAY)"

    if [ "$i" -eq "$USER_COUNT" ]; then
        echo "('$phone', $createTime);" >> "$SQL_FILE"
    else
        echo "('$phone', $createTime)," >> "$SQL_FILE"
    fi
done

echo "执行用户插入..."
$MYSQL_CMD < "$SQL_FILE"

# ---------- 2. 查出刚插入用户的真实 id（按手机号范围反查，比记录自增id更稳）----------
echo "查询用户真实 ID..."
$MYSQL_CMD -N -B -e "SELECT id FROM user WHERE phone LIKE '1390000%' ORDER BY id" > /tmp/user_ids_$$.txt

# ---------- 3. 生成历史订单（数量按用户类型区分：正常用户0~50单，羊毛党0单）----------
ORDER_SQL_FILE="/tmp/gen_orders_$$.sql"
echo "USE \`$DB_NAME\`;" > "$ORDER_SQL_FILE"

paste /tmp/user_ids_$$.txt "$USER_TYPE_FILE" | while read -r userId userType; do
    if [ "$userType" == "bot" ]; then
        orderCount=0   # 羊毛党画像：0 单
    else
        orderCount=$((RANDOM % 51))   # 正常用户画像：0~50 单
    fi

    for ((j = 0; j < orderCount; j++)); do
        # 用 userId*100000 + 序号 构造一个唯一订单 id，避免和真实雪花 id 冲突（真实雪花 id 位数远大于这个范围）
        orderId=$((userId * 100000 + j))
        daysAgo=$((RANDOM % 365))
        echo "INSERT INTO voucher_order (id, user_id, voucher_id, pay_type, status, create_time, update_time)
              VALUES ($orderId, $userId, $HISTORY_VOUCHER_ID, 1, 2, DATE_SUB(NOW(), INTERVAL $daysAgo DAY), NOW());" >> "$ORDER_SQL_FILE"
    done
done

echo "执行订单插入..."
$MYSQL_CMD < "$ORDER_SQL_FILE"

# 清理临时文件
rm -f "$SQL_FILE" "$ORDER_SQL_FILE" "$USER_TYPE_FILE" /tmp/user_ids_$$.txt

echo "✓ 完成"
echo "----------------------------------------"
echo "下一步：运行 migrate_legacy_users_to_redis.sh 把这批数据同步到 Redis"
echo "========================================"
