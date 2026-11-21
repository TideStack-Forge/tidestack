#!/bin/bash

# 在/etc/hosts文件最后添加一行
echo "127.0.0.1 hostname" >> /etc/hosts

# 如果必要目录不存在，则创建
mkdir -p "/home/public/application/data"
mkdir -p "/home/public/application/logs/web"
mkdir -p "/home/public/application/logs/service"

# 启动nginx
nginx

if [ "${DEBUG:-false}" = "true" ]; then
  # 远程调试模式
  sh /home/private/application/service/service -d start
else
  # 正常模式
  sh /home/private/application/service/service start
fi