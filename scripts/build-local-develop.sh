#!/bin/bash
set -e

VERSION="1.0.0-beta.2"
REGISTRY="registry.cn-hangzhou.aliyuncs.com"
NAMESPACE="ouroboros"
BASE_IMAGE="$REGISTRY/$NAMESPACE/openjdk:8-jdk-slim"
WEB_IMAGE="$REGISTRY/$NAMESPACE/ouroboros-web:latest"

echo "=========================================="
echo "构建本地 ouroboros-develop 镜像"
echo "版本: $VERSION"
echo "=========================================="

cd "$(dirname "$0")/../../.."

ensure_frontend_template_dependencies() {
  local frontend_root_node_modules="frontends/web/node_modules"
  local frontend_main_node_modules="frontends/web/main-app/node_modules"

  if [ -d "$frontend_root_node_modules" ] && [ -d "$frontend_main_node_modules" ]; then
    return
  fi

  bash scripts/ci/verify-node-npm-baseline.sh

  if [ ! -d "$frontend_root_node_modules" ]; then
    echo "安装项目模板校验所需前端工作区依赖"
    (
      cd frontends/web
      npm ci --legacy-peer-deps
    )
  fi

  if [ ! -d "$frontend_main_node_modules" ]; then
    echo "安装项目模板校验所需主前端依赖"
    (
      cd frontends/web/main-app
      npm ci --legacy-peer-deps
    )
  fi
}

echo ""
echo "步骤 1/4: 构建 Maven 项目"
echo "=========================================="
mvn versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
cd bom && mvn clean && mvn install && cd ..
mvn clean
mvn install -P 'jdk8,!jdk17' -Dpmd.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true -DskipTests

echo ""
echo "步骤 2/4: 准备 Mothership Docker 目录"
echo "=========================================="
cd platform/ouroboros-mothership

THIRD_PARTY_LIB_PATH="$(pwd)/docker/common-layer/app/base/service/third-party-lib"
OUROBOROS_LIB_PATH="$(pwd)/docker/build-layer/app/base/service/ouroboros-lib"

echo "清空发布目录"
rm -rf ./docker/build-layer/
rm -rf ./docker/common-layer/

echo "拷贝依赖包"
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${THIRD_PARTY_LIB_PATH}" -DexcludeGroupIds=com.ouroboros
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${OUROBOROS_LIB_PATH}" -DincludeGroupIds=com.ouroboros

rm -f ${THIRD_PARTY_LIB_PATH}/*.pom
rm -f ${OUROBOROS_LIB_PATH}/*.pom

cp "target/ouroboros-mothership-${VERSION}.jar" ./docker/build-layer/app/base/service/ouroboros-mothership.jar

echo ""
echo "步骤 2.5/4: 构建 Mothership 镜像"
echo "=========================================="
cd docker
docker build \
  --build-arg BASE_IMAGE=${BASE_IMAGE} \
  --build-arg WEB_IMAGE=${WEB_IMAGE} \
  --platform linux/amd64 \
  -t ${REGISTRY}/${NAMESPACE}/ouroboros-mothership:${VERSION} \
  .
cd ../..

echo ""
echo "步骤 3/4: 准备 Develop Docker 目录"
echo "=========================================="
cd platform/ouroboros-develop

COMMON_LAYER_PATH="$(pwd)/docker/common-layer"
BUILD_LAYER_PATH="$(pwd)/docker/build-layer"
TEMPLATE_LAYER_PATH="$(pwd)/docker/template-layer"
COMMON_LAYER_APP_EXTEND_PATH="${COMMON_LAYER_PATH}/app/metadata/extend"
BUILD_LAYER_APP_EXTEND_PATH="${BUILD_LAYER_PATH}/app/metadata/extend"
DEVELOP_SUPPORT_PATH="${BUILD_LAYER_PATH}/app/develop-support"
DEVELOP_DOCS_PATH="${BUILD_LAYER_PATH}/app/base/web/dev-docs"
PROJECT_TEMPLATES_PATH="${TEMPLATE_LAYER_PATH}/app/project-templates"

echo "清空发布目录"
rm -rf "${COMMON_LAYER_PATH}"
rm -rf "${BUILD_LAYER_PATH}"
rm -rf "${TEMPLATE_LAYER_PATH}"

echo "拷贝依赖包"
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${COMMON_LAYER_APP_EXTEND_PATH}" -DexcludeGroupIds=com.ouroboros
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${BUILD_LAYER_APP_EXTEND_PATH}" -DincludeGroupIds=com.ouroboros

rm -f ${COMMON_LAYER_APP_EXTEND_PATH}/*.pom
rm -f ${BUILD_LAYER_APP_EXTEND_PATH}/*.pom

echo "删除 mothership 中已存在的依赖"
find ../ouroboros-mothership/docker/common-layer/app/base/service/third-party-lib -name "*.*" 2>/dev/null | while read -r file; do
  name=$(basename "$file")
  rm -f "${COMMON_LAYER_APP_EXTEND_PATH}/$name"
done

rm -f "${COMMON_LAYER_APP_EXTEND_PATH}/flowable-event-registry-6.8.1.jar"

find ../ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-lib -name "*.*" 2>/dev/null | while read -r file; do
  name=$(basename "$file")
  rm -f "${BUILD_LAYER_APP_EXTEND_PATH}/$name"
done

cd ../..

echo "构建 handbook 文档"
bash scripts/build-handbook-dev-docs.sh "${DEVELOP_DOCS_PATH}"

echo "拷贝 dev-helper"
mkdir -p "${DEVELOP_SUPPORT_PATH}"
cp "dev-module/ouroboros-dev-helper/ouroboros-dev-helper-core/target/ouroboros-dev-helper-core-${VERSION}.jar" "${DEVELOP_SUPPORT_PATH}/ouroboros-dev-helper-core.jar"
cp "dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime/target/ouroboros-dev-helper-runtime-${VERSION}.jar" "${DEVELOP_SUPPORT_PATH}/ouroboros-dev-helper-runtime.jar"
cp "libs/common/common-dev/target/common-dev-${VERSION}.jar" "${DEVELOP_SUPPORT_PATH}/ouroboros-common-dev.jar"
cp "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/target/ouroboros-logicflow-debug-helper-${VERSION}.jar" "${DEVELOP_SUPPORT_PATH}/ouroboros-logicflow-debug-helper.jar"

echo "生成项目模板发布产物"
ensure_frontend_template_dependencies
bash project-templates/scripts/verify-frontend-templates.sh --qualification --clean
bash project-templates/scripts/verify-backend-templates.sh --qualification --clean --platform-smoke
bash project-templates/scripts/verify-template-publication.sh --clean

echo "拷贝项目模板"
rm -rf "${PROJECT_TEMPLATES_PATH}"
mkdir -p "${PROJECT_TEMPLATES_PATH}/catalog" "${PROJECT_TEMPLATES_PATH}/artifacts"
cp "project-templates/dist/catalog/stable-catalog.json" \
  "${PROJECT_TEMPLATES_PATH}/catalog/stable-catalog.json"
cp -R "project-templates/dist/artifacts/." "${PROJECT_TEMPLATES_PATH}/artifacts/"

echo ""
echo "步骤 4/4: 构建 Develop 镜像"
echo "=========================================="
cd platform/ouroboros-develop/docker
docker build \
  --build-arg BASE_IMAGE=${REGISTRY}/${NAMESPACE}/ouroboros-mothership:${VERSION} \
  --platform linux/amd64 \
  -t ${REGISTRY}/${NAMESPACE}/ouroboros-develop:${VERSION} \
  .

cd ../../..

echo ""
echo "=========================================="
echo "✅ 构建完成"
echo "=========================================="
echo "镜像: ${REGISTRY}/${NAMESPACE}/ouroboros-develop:${VERSION}"
echo ""
echo "验证镜像："
docker images | grep ouroboros-develop | grep ${VERSION}
