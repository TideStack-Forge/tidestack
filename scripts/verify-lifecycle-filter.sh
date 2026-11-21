#!/bin/bash
# 验证 LifecycleGuardFilter 是否拦截 /actuator/health

set -e

echo "=========================================="
echo "验证 LifecycleGuardFilter 拦截行为"
echo "=========================================="

# 配置
BACKEND_PORT=${BACKEND_PORT:-8080}
NGINX_PORT=${NGINX_PORT:-80}
MAX_WAIT=60

echo ""
echo "测试配置："
echo "  后端端口: $BACKEND_PORT"
echo "  Nginx端口: $NGINX_PORT"
echo ""

# 函数：检查HTTP状态码
check_status() {
    local url=$1
    local desc=$2
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    echo "  $desc: $status"
    echo "$status"
}

# 函数：等待Spring Boot启动
wait_for_spring_boot() {
    echo "等待Spring Boot启动..."
    local count=0
    while [ $count -lt $MAX_WAIT ]; do
        if curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1; then
            echo "  Spring Boot已启动"
            return 0
        fi
        count=$((count + 1))
        sleep 1
    done
    echo "  ⚠️  超时：Spring Boot未在${MAX_WAIT}秒内启动"
    return 1
}

echo "=========================================="
echo "阶段1: 检测生命周期未完成时的行为"
echo "=========================================="
echo ""
echo "在Spring Boot启动后、生命周期完成前测试..."
echo ""

# 启动容器（如果未启动）
if ! docker ps | grep -q ouroboros-develop; then
    echo "启动容器..."
    cd scripts/deploy/develop-docker-compose
    docker-compose up -d
    cd -
fi

# 等待Spring Boot启动
wait_for_spring_boot

# 在生命周期完成前快速测试
echo "测试1: 直接访问后端（绕过nginx）"
HEALTH_STATUS=$(check_status "http://localhost:$BACKEND_PORT/actuator/health" "/actuator/health")
LOGIN_STATUS=$(check_status "http://localhost:$BACKEND_PORT/api/auth/login" "/api/auth/login")

echo ""
if [ "$HEALTH_STATUS" == "503" ] && [ "$LOGIN_STATUS" == "503" ]; then
    echo "✅ 结果：两者都返回503，LifecycleGuardFilter正确拦截了actuator端点"
elif [ "$HEALTH_STATUS" == "503" ] || [ "$LOGIN_STATUS" == "503" ]; then
    echo "⚠️  结果：只有一个返回503，行为不一致"
    echo "   Health: $HEALTH_STATUS, Login: $LOGIN_STATUS"
else
    echo "ℹ️  结果：生命周期可能已完成，两者都不返回503"
    echo "   Health: $HEALTH_STATUS, Login: $LOGIN_STATUS"
fi

echo ""
echo "测试2: 通过nginx访问"
NGINX_HEALTH_STATUS=$(check_status "http://localhost:$NGINX_PORT/actuator/health" "nginx -> /actuator/health")
NGINX_API_STATUS=$(check_status "http://localhost:$NGINX_PORT/api/auth/login" "nginx -> /api/auth/login")

echo ""
if [ "$NGINX_HEALTH_STATUS" == "404" ]; then
    echo "⚠️  问题：nginx返回404，说明缺少/actuator/路径的反代配置"
else
    echo "ℹ️  nginx正确代理了/actuator/路径"
fi

echo ""
echo "=========================================="
echo "阶段2: 等待生命周期完成后测试"
echo "=========================================="
echo ""

# 等待生命周期完成
echo "等待生命周期完成（检测登录API返回非503）..."
count=0
while [ $count -lt $MAX_WAIT ]; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$BACKEND_PORT/api/auth/login" 2>/dev/null || echo "000")
    if [ "$STATUS" != "503" ] && [ "$STATUS" != "000" ]; then
        echo "  生命周期已完成（登录API返回$STATUS）"
        break
    fi
    count=$((count + 1))
    sleep 1
done

if [ $count -ge $MAX_WAIT ]; then
    echo "  ⚠️  超时：生命周期未在${MAX_WAIT}秒内完成"
    exit 1
fi

echo ""
echo "测试3: 生命周期完成后访问"
HEALTH_AFTER=$(check_status "http://localhost:$BACKEND_PORT/actuator/health" "/actuator/health")
LOGIN_AFTER=$(check_status "http://localhost:$BACKEND_PORT/api/auth/login" "/api/auth/login")

echo ""
if [ "$HEALTH_AFTER" == "200" ]; then
    echo "✅ 生命周期完成后，/actuator/health返回200"
else
    echo "⚠️  生命周期完成后，/actuator/health返回$HEALTH_AFTER（预期200）"
fi

echo ""
echo "=========================================="
echo "总结"
echo "=========================================="
echo ""
echo "1. LifecycleGuardFilter是否拦截actuator端点？"
if [ "$HEALTH_STATUS" == "503" ]; then
    echo "   ✅ 是，会拦截"
else
    echo "   ❌ 否，不拦截（或生命周期已完成）"
fi

echo ""
echo "2. Nginx是否正确代理actuator路径？"
if [ "$NGINX_HEALTH_STATUS" == "404" ]; then
    echo "   ❌ 否，需要添加/actuator/路径的反代配置"
else
    echo "   ✅ 是"
fi

echo ""
echo "3. 建议："
if [ "$NGINX_HEALTH_STATUS" == "404" ]; then
    echo "   - 在nginx.conf中添加："
    echo "     location ^~ /actuator/ {"
    echo "         proxy_pass http://localhost:8080;"
    echo "         # ... 其他proxy配置"
    echo "     }"
fi

if [ "$HEALTH_STATUS" == "503" ]; then
    echo "   - LifecycleGuardFilter正确拦截了actuator端点"
    echo "   - 可以使用/actuator/health作为健康检查端点"
    echo "   - 预期行为：生命周期未完成时返回503，完成后返回200"
fi

echo ""
