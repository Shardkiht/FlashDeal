#!/bin/bash
# scripts/init_users.sh
# 预插入 5000 个测试用户到数据库
# 用法: ./scripts/init_users.sh [用户数]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

USER_COUNT=${1:-5000}
# 从 application-dev.yaml 读取数据库配置
APP_YAML="$SCRIPT_DIR/../src/main/resources/application-dev.yaml"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-sky_take_out}"
DB_USER="${DB_USER:-}"
DB_PASS="${DB_PASS:-}"

if [ -f "$APP_YAML" ]; then
    DB_NAME=$(grep -o 'jdbc:mysql://[^?]*' "$APP_YAML" | sed 's/.*\/\(.*\)/\1/')
    DB_USER=$(awk '/datasource:/{flag=1} flag && /username:/{print $2; exit}' "$APP_YAML" | tr -d ' ')
    DB_PASS=$(awk '/datasource:/{flag=1} flag && /password:/{print $2; exit}' "$APP_YAML" | tr -d ' ')
fi

echo "========================================"
echo " 预插入 $USER_COUNT 个测试用户"
echo " 数据库: $DB_NAME@$DB_HOST:$DB_PORT"
echo "========================================"

# 生成 INSERT SQL
SQL_FILE="/tmp/init_users_$$.sql"

echo "USE \`$DB_NAME\`;" > "$SQL_FILE"
echo "INSERT INTO user (phone, create_time) VALUES" >> "$SQL_FILE"

for i in $(seq 1 "$USER_COUNT"); do
    phone="1380000$(printf '%04d' $i)"
    if [ "$i" -eq "$USER_COUNT" ]; then
        echo "('$phone', NOW());" >> "$SQL_FILE"
    else
        echo "('$phone', NOW())," >> "$SQL_FILE"
    fi
done

echo "执行 SQL..."
# 检测容器运行时并执行 SQL
CONTAINER_NAME="mysql8"
SQL_FILE="/tmp/init_users_$$.sql"

if command -v podman &> /dev/null && podman ps | grep -q "$CONTAINER_NAME"; then
    podman cp "$SQL_FILE" "$CONTAINER_NAME:/tmp/init_users.sql"
    podman exec -i "$CONTAINER_NAME" sh -c "mysql -u '$DB_USER' -p'$DB_PASS' < /tmp/init_users.sql"
elif command -v docker &> /dev/null && docker ps | grep -q "$CONTAINER_NAME"; then
    docker cp "$SQL_FILE" "$CONTAINER_NAME:/tmp/init_users.sql"
    docker exec -i "$CONTAINER_NAME" sh -c "mysql -u '$DB_USER' -p'$DB_PASS' < /tmp/init_users.sql"
else
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" < "$SQL_FILE"
fi

rm -f "$SQL_FILE"

echo "✓ 完成"
echo "========================================"
