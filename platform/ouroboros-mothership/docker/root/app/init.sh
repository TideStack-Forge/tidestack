#!/bin/sh
# 确保web部署文件的访问权限正确
umask 0022

# 在/etc/hosts文件最后添加一行
grep -qF "127.0.0.1 hostname" /etc/hosts || echo "127.0.0.1 hostname" >>/etc/hosts

# 如果 logs/service、 logs/web 目录不存在，则创建
if [ ! -d "/app/deploy/logs/service" ]; then
  mkdir -p "/app/deploy/logs/service"
fi
if [ ! -d "/app/deploy/logs/web" ]; then
  mkdir -p "/app/deploy/logs/web"
fi

# 重启时保留部署元数据的身份；此前创建的软链接不属于镜像内建元数据。
if [ ! -d "/app/metadata" ] || { [ -L "/app/metadata" ] && [ "$(readlink "/app/metadata")" = "/app/deploy/metadata" ]; }; then
  if [ -d "/app/deploy/metadata" ]; then
    # 如果元数据来自于部署，则默认启用部署级扩展
    ENABLE_DEPLOY_EXTEND=${ENABLE_DEPLOY_EXTEND:-true}
    export ENABLE_DEPLOY_EXTEND
    if [ ! -L "/app/metadata" ]; then
      ln -s "/app/deploy/metadata" "/app/metadata"
    fi
  fi
else
  # 如果元数据是内置的，则默认禁用部署级扩展，减少安全隐患
  ENABLE_DEPLOY_EXTEND=${ENABLE_DEPLOY_EXTEND:-false}
  export ENABLE_DEPLOY_EXTEND
fi

# 判断 ENABLE_NGINX 是否为 true，如果是，则启动 nginx
if [ "${ENABLE_NGINX:-true}" = "true" ]; then
  nginx
fi

# 以 exec 方式启动服务，以确保 PID 为 1
exec /app/base/service/start.sh
