#!/bin/bash

# 脚本用于发布 ouroboros-sdk 到 npm 仓库
#
# 命令行参数：
# -v 版本号 必填
# -r 发布仓库地址 选填，默认为 npm 官方镜像
# -m 安装仓库地址 选填
# -h 打印帮助信息
#
# 例子：./publish-ouroboros-sdk.sh -v 1.0.0 -r https://registry.npm.taobao.org/
#
# 注意：
# 1. 目前只提供 shell 脚本，未提供 windows 脚本
# 2. 请确保已经安装了 npm 包管理工具 npm
# 3. 请确保已经登录 npm 账号，或者在环境变量中设置了 NPM_TOKEN

# 获取 -v 和 -r 后面的参数值
publish_registry="https://registry.npmjs.org/"
install_registry=""

set -e

while getopts "v:r:m:h" opt; do
  case $opt in
  v)
    version=$OPTARG
    ;;
  r)
    publish_registry=$OPTARG
    ;;
  m)
    install_registry=$OPTARG
    ;;
  h)
    echo "用法： -v 版本号 [-p 发布仓库地址] [-i 安装仓库地址]"
    echo "参数说明："
    echo "-v <版本号>\tSDK版本号，格式为 1.0.0，后面可加预发行版本信息如：1.0.0-alpha.1"
    echo "-r <仓库地址>\t发布的 npm 仓库地址，默认为 npm 官方仓库"
    echo "-m <镜像地址>\t构建时安装依赖用的npm镜像地址"
    echo "-h \t\t打印帮助信息"
    exit 0
    ;;
  \?)
    echo "无效的选项：-$OPTARG" >&2
    exit 1
    ;;
  :)
    echo "选项 -$OPTARG 需要参数" >&2
    exit 1
    ;;
  esac
done

if [ -z "$version" ]; then
  echo "版本号不能为空"
  exit 1
fi

echo "正在发布 ouroboros-sdk"
echo "版本号：$version"
echo "发布地址：$publish_registry"
# 如果设置了 npm install 镜像地址，则输出 npm install 镜像地址
if [ -n "$install_registry" ]; then
  echo "npm install 镜像地址：$install_registry"
fi

# 输出认证方式(如果有 NPM_TOKEN 则代表使用 NPM_TOKEN，否则如果 npm 已登录则代表使用 npm 登录，最后表示为无认证)

# 检查是否有 NPM_TOKEN 环境变量
echo "检查认证方式..."
echo "检查 NPM_TOKEN"
HAS_TOKEN=$(echo "$NPM_TOKEN" | grep -o '^[a-zA-Z0-9_-]\+$' || true)
echo "检查 npm 是否已登陆 $publish_registry"
# 静默检查 npm 是否已登录
HAS_LOGIN=$(npm whoami --registry=$publish_registry 2>/dev/null || true)

if [ -n "$HAS_TOKEN" ]; then
  echo "认证方式：NPM_TOKEN"
elif [ -n "$HAS_LOGIN" ]; then
  echo "认证方式：npm 登录"
else
  echo "认证方式：无认证"
fi

bash ../../scripts/ci/verify-node-npm-baseline.sh

# 切换到 sdk 目录
cd ouroboros-sdk

# 更新版本号
npm version $version 2>/dev/null || true

if [ -n "$install_registry" ]; then
  npm ci --registry="$install_registry"
else
  npm ci
fi

# 打包 SDK
npm run build

# 若有 NPM_TOKEN 则准备 .npmrc 文件
if [ -n "$HAS_TOKEN" ]; then
  # 如果有 .npmrc 文件，先备份
  if [ -f .npmrc ]; then
    RESTORE_NPMRC=true
    # 生成一个随机文件名，取basename
    BACKUP_NPMRC_FILE=.npmrc.back-$(basename $(mktemp))
    mv .npmrc $BACKUP_NPMRC_FILE
  else
    REMOVE_NPMRC=true
  fi
  echo "registry=$publish_registry" >>.npmrc
  # 提取registry的域名部分，去掉前置协议和后置/
  publish_registry_domain=${publish_registry#*://}
  publish_registry_domain=${publish_registry_domain%%/*}
  echo "//${publish_registry_domain}/:always-auth=true" >>.npmrc
  echo "//${publish_registry_domain}/:_authToken=$NPM_TOKEN" >>.npmrc
fi

# 发布 SDK
if [[ "$version" =~ -(alpha|beta|rc)(\.|-|$) ]]; then
  tag="${BASH_REMATCH[1]}"
  npm publish --access public --tag "latest"
else
  npm publish --access public
fi

# 还原 .npmrc 文件
if [ $REMOVE_NPMRC = true ]; then
  rm .npmrc
elif [ $RESTORE_NPMRC = true ]; then
  rm .npmrc
  mv $BACKUP_NPMRC_FILE .npmrc
fi

cd -
echo "发布完成"
