#!/usr/bin/env bash
#
# 快速验证：只构建目标模块及其上游，跳过质量门禁，并行执行。
#
# 为什么需要这个脚本（实测数据，2026-09-11）：
#   mvn -o -pl <module> -am -Dtest=X test                        131s   ← 默认路径
#   mvn -o -pl <module> -am -Dtest=X test + 跳门禁 + -T 1C         38s   ← 本脚本
#   mvn -o -pl <module> -Dtest=X test（不带 -am，用已安装的依赖）    46s
#
# 主要浪费来自两处：
#   1. 质量门禁（checkstyle/pmd/jacoco/enforcer）会在**每个上游模块**各跑一遍，
#      而它们不是编译/测试正确性的必要条件——提交前或 CI 再跑；
#   2. -am 会拉入几十个上游模块，串行构建。
#
# 为什么不能"预装依赖到工作区仓库再彻底去掉 -am"：
#   - 沙箱不允许写 ~/.m2（install 被拒），
#   - 离线模式下空的工作区仓库解析不到外部 BOM（spring-boot-dependencies 等）。
#   因此 -am 仍是获得"上游最新代码"的唯一途径；本脚本把它压到可接受成本。
#
# 用法：
#   bash scripts/agent-feedback/verify-fast.sh <模块路径> [maven 参数...]
#   bash scripts/agent-feedback/verify-fast.sh \
#       security/ouroboros-web-security/ouroboros-web-security-rbac-manage \
#       -Dtest=UserSsoEnterpriseIdentityMappingServiceTest
#
# 不带 -Dtest 时跑该模块的完整测试集。
# 提交前请另跑一次带质量门禁的构建（去掉 SKIP_GATES=1）。
set -euo pipefail

if [ $# -lt 1 ]; then
  sed -n '1,30p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

MODULES="$1"
shift

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

# 每个 -Dtest 目标都要求 surefire 在"没有匹配用例"的模块上不要失败。
EXTRA_ARGS=(-Dsurefire.failIfNoSpecifiedTests=false)

if [ "${SKIP_GATES:-1}" = "1" ]; then
  EXTRA_ARGS+=(
    -Dcheckstyle.skip
    -Dpmd.skip
    -Djacoco.skip
    -Denforcer.skip
    -Dmaven.javadoc.skip=true
  )
fi

exec mvn -o -T "${THREADS:-1C}" \
  -pl "$MODULES" -am \
  "${EXTRA_ARGS[@]}" \
  "$@" \
  test
