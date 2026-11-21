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
MAVEN_REPO_LOCAL=${MAVEN_REPO_LOCAL:-"${repo_root}/.m2/repository"}
# 导出给子进程：build.sh 通过 MAVEN_REPO_LOCAL 决定 mvn 的本地仓，
# 不导出会让 build 阶段退回 ~/.m2（与本脚本其余阶段用的不是同一个仓）。
export MAVEN_REPO_LOCAL
STATUS_FILE=${STATUS_FILE:-".${VERSION}.preflight-build-status"}
BUILD_TESTS_ENABLED=${BUILD_TESTS_ENABLED:-true}
GATE_CHECKS_ENABLED=${GATE_CHECKS_ENABLED:-true}
RUN_STATISTICS_E2E=${RUN_STATISTICS_E2E:-false}
PREPARE_LOCAL_REACTOR_ARTIFACTS=${PREPARE_LOCAL_REACTOR_ARTIFACTS:-true}
REGISTRY_URL=${REGISTRY_URL:-registry.cn-hangzhou.aliyuncs.com}
NAMESPACE=${NAMESPACE:-ouroboros}
WEB_IMAGE_TAG=${WEB_IMAGE_TAG:-latest}
MOTHERSHIP_IMAGE_TAG=${MOTHERSHIP_IMAGE_TAG:-$VERSION}
BASE_IMAGE=${BASE_IMAGE:-registry.cn-hangzhou.aliyuncs.com/ouroboros/eclipse-temurin:21-jdk-noble}
TAGS=${TAGS:-$VERSION}
KEEP_PREFLIGHT_STATUS=${KEEP_PREFLIGHT_STATUS:-false}

mkdir -p "$MAVEN_REPO_LOCAL"

cleanup_status_file() {
  if [ "$KEEP_PREFLIGHT_STATUS" != true ]; then
    rm -f "$STATUS_FILE"
  fi
}
trap cleanup_status_file EXIT

# The preflight status file is only an intra-run handoff between build stages.
# Never reuse a stale file from an earlier or aborted run, because gate stages
# may regenerate local reports before the build stage validates the fingerprint.
rm -f "$STATUS_FILE"

echo "后端 preflight"
echo "版本号：$VERSION"
echo "Maven 本地仓库：$MAVEN_REPO_LOCAL"
echo "状态文件：$STATUS_FILE"
echo "门控检查：$GATE_CHECKS_ENABLED"
echo "构建测试：$BUILD_TESTS_ENABLED"
echo "统计报表 E2E：$RUN_STATISTICS_E2E"
echo "预装本地 reactor 产物：$PREPARE_LOCAL_REACTOR_ARTIFACTS"
echo "Docker registry/namespace：$REGISTRY_URL/$NAMESPACE"
echo "Web 镜像标签：$WEB_IMAGE_TAG"
echo "Mothership 基础镜像：$BASE_IMAGE"
echo "本地镜像标签：$TAGS"

if [ "$GATE_CHECKS_ENABLED" = true ]; then
  run_stage verify-ai-runtime-hygiene bash scripts/ci/verify-ai-runtime-hygiene.sh
  run_stage verify-dependency-policy bash scripts/ci/verify-dependency-policy.sh
  run_stage verify-node-npm-baseline bash scripts/ci/verify-node-npm-baseline.sh
fi

run_stage prepare-maven-version mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false

if [ "$GATE_CHECKS_ENABLED" = true ]; then
  run_stage verify-checkstyle mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" checkstyle:check
  if [ "$PREPARE_LOCAL_REACTOR_ARTIFACTS" = true ]; then
    # Jenkins agents may already have same-version reactor artifacts in their Maven cache.
    # A clean local preflight uses a worktree-local .m2, so install reactor outputs before
    # standalone plugin phases resolve intra-repository module dependencies. This still
    # avoids Maven deploy and does not publish any artifact outside the local repository.
    run_stage prepare-local-reactor-artifacts mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -DskipTests -Dpmd.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true install
  fi
  run_stage verify-pmd mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true pmd:check
  run_stage verify-package-structure bash scripts/architecture/check-package-structure.sh
  run_stage verify-content-storage bash testing/integration/feature/storage/independent-content-stores/run.sh --base HEAD
  run_stage verify-architecture-compile mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -DskipTests -Dpmd.skip=true -Dcheckstyle.skip=true test-compile
  run_stage verify-architecture-test mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -pl testing/architecture-tests -am test -Dtest=ArchitectureTestWithAllowlist -Dsurefire.failIfNoSpecifiedTests=false
  run_stage verify-doc-links bash scripts/docs/check-links.sh
  run_stage verify-doc-quality bash scripts/docs/check-doc-quality.sh
  run_stage verify-hardcoded-config bash scripts/architecture/check-hardcoded-config.sh
  if [ "$RUN_STATISTICS_E2E" = true ]; then
    run_stage verify-statistics-reporting-e2e bash scripts/ci/verify-statistics-reporting-e2e.sh
  fi
fi

run_stage build-root ./build.sh build -v "$VERSION" -s "$STATUS_FILE" -c "$BUILD_TESTS_ENABLED"
run_stage build-local-docker ./build.sh preflight \
  -v "$VERSION" \
  -w "$WEB_IMAGE_TAG" \
  -e "$MOTHERSHIP_IMAGE_TAG" \
  -b "$BASE_IMAGE" \
  -r "$REGISTRY_URL" \
  -n "$NAMESPACE" \
  -c "$BUILD_TESTS_ENABLED" \
  -t "$TAGS" \
  -s "$STATUS_FILE"

echo "后端 preflight 完成"
