#!/bin/bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "用法: $0 <target-dev-docs-dir>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
HANDBOOK_DIR="${ROOT_DIR}/docs/handbook"
TARGET_DIR="$1"
DOCS_BASE_URL_VALUE="/dev-docs/"

if [[ "${TARGET_DIR}" != /* ]]; then
  TARGET_DIR="${ROOT_DIR}/${TARGET_DIR}"
fi

if [ ! -f "${HANDBOOK_DIR}/package.json" ]; then
  echo "未找到 handbook 项目: ${HANDBOOK_DIR}" >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "构建 handbook 需要 Node.js >= ${REQUIRED_NODE_MAJOR}.0，当前未找到 node 命令" >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "构建 handbook 需要 npm，当前未找到 npm 命令" >&2
  exit 1
fi

bash "${ROOT_DIR}/scripts/ci/verify-node-npm-baseline.sh"

echo "构建 handbook 文档"
rm -rf "${HANDBOOK_DIR}/build"
(
  cd "${HANDBOOK_DIR}"
  npm ci --no-audit --no-fund
  DOCS_BASE_URL="${DOCS_BASE_URL_VALUE}" npm run build
)

if ! grep -R -a -m 1 "${DOCS_BASE_URL_VALUE}" "${HANDBOOK_DIR}/build" >/dev/null; then
  echo "handbook 构建产物缺少 ${DOCS_BASE_URL_VALUE} 前缀，请检查 Docusaurus baseUrl" >&2
  exit 1
fi

echo "拷贝 handbook 到 ${TARGET_DIR}"
rm -rf "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}"
cp -R "${HANDBOOK_DIR}/build/." "${TARGET_DIR}/"
