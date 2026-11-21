#!/bin/bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

read_project_version() {
  awk '
    /<version>/ {
      gsub(/.*<version>|<\/version>.*/, "")
      print
      exit
    }
  ' pom.xml
}

run_stage() {
  local stage=$1
  shift
  echo "==> ${stage}"
  "$@"
}

VERSION=${VERSION:-$(read_project_version)}
REGISTRY_URL=${REGISTRY_URL:-registry.cn-hangzhou.aliyuncs.com}
NAMESPACE=${NAMESPACE:-ouroboros}
NPM_INSTALL_REGISTRY=${NPM_INSTALL_REGISTRY:-https://registry.npmmirror.com}
NPM_CONFIG_CACHE=${NPM_CONFIG_CACHE:-"${repo_root}/.npm-cache"}
BASE_IMAGE=${BASE_IMAGE:-swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/nginx:1.28-alpine}
TAGS=${TAGS:-}
# npm 10 只认小写 npm_config_cache；大写 NPM_CONFIG_CACHE 不会生效，
# 缓存会回落到 ~/.npm（可能含历史 root 属主文件或不可写），导致 npm ci EPERM。
export npm_config_cache="$NPM_CONFIG_CACHE"

echo "前端 web preflight"
echo "版本号：$VERSION"
echo "Docker registry/namespace：$REGISTRY_URL/$NAMESPACE"
echo "基础镜像：$BASE_IMAGE"
echo "NPM registry：$NPM_INSTALL_REGISTRY"
echo "NPM cache：$NPM_CONFIG_CACHE"
if [ -n "$TAGS" ]; then
  echo "本地镜像标签：$TAGS"
fi

# Web preflight builds the application with the publisher's own lockfile and
# container toolchain. Keep the host check to command availability so an
# unrelated local Node/npm patch version does not block that build.
run_stage verify-node-npm-baseline env NODE_NPM_BASELINE_MODE=presence bash scripts/ci/verify-node-npm-baseline.sh
run_stage prepare chmod +x frontends/web/publish-ouroboros-web.sh

publish_args=(
  -v "$VERSION"
  -r "$REGISTRY_URL"
  -n "$NAMESPACE"
  -b "$BASE_IMAGE"
  -m "$NPM_INSTALL_REGISTRY"
  -l
)

if [ -n "$TAGS" ]; then
  publish_args+=(-t "$TAGS")
fi

run_stage build-local-web-image bash -c 'cd frontends/web && ./publish-ouroboros-web.sh "$@"' _ "${publish_args[@]}"

echo "前端 web preflight 完成"
