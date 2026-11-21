#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BASELINE_MODE="${NODE_NPM_BASELINE_MODE:-strict}"

ACTUAL_NODE="$(node --version 2>/dev/null || true)"
ACTUAL_NPM="$(npm --version 2>/dev/null || true)"

if [ "${BASELINE_MODE}" = "presence" ]; then
  if [ -z "${ACTUAL_NODE}" ] || [ -z "${ACTUAL_NPM}" ]; then
    echo "Node/npm 不可用: presence 模式仍要求 node 和 npm 命令可执行" >&2
    exit 1
  fi
  echo "Node/npm presence OK: ${ACTUAL_NODE} / npm ${ACTUAL_NPM} (version checks disabled)"
  exit 0
fi

if [ "${BASELINE_MODE}" != "strict" ]; then
  echo "未知 Node/npm 基线模式: ${BASELINE_MODE}（可选 strict 或 presence）" >&2
  exit 1
fi

if [ ! -f "${ROOT_DIR}/.nvmrc" ]; then
  echo "缺少 Node 基线声明: ${ROOT_DIR}/.nvmrc" >&2
  exit 1
fi

# 版本比较：$1 >= $2（按点分段数值比较，忽略前缀 v 与非数字后缀）。
version_ge() {
  local left="${1#v}"
  local right="${2#v}"
  local IFS='.'
  local -a l=($left)
  local -a r=($right)
  local i x y
  for ((i = 0; i < ${#r[@]}; i++)); do
    x="${l[i]:-0}"; y="${r[i]:-0}"
    x="${x%%[!0-9]*}"; y="${y%%[!0-9]*}"
    if [ "$((10#${x:-0}))" -gt "$((10#${y:-0}))" ]; then return 0; fi
    if [ "$((10#${x:-0}))" -lt "$((10#${y:-0}))" ]; then return 1; fi
  done
  for ((i = ${#r[@]}; i < ${#l[@]}; i++)); do
    x="${l[i]}"; x="${x%%[!0-9]*}"
    if [ "$((10#${x:-0}))" -gt 0 ]; then return 0; fi
  done
  return 0
}

EXPECTED_NODE="$(tr -d '[:space:]' < "${ROOT_DIR}/.nvmrc")"
EXPECTED_NPM="$(
  ROOT_DIR="${ROOT_DIR}" node <<'NODE'
const fs = require('node:fs')
const path = require('node:path')
const pkg = JSON.parse(fs.readFileSync(path.join(process.env.ROOT_DIR, 'package.json'), 'utf8'))
const packageManager = pkg.packageManager || ''
const match = packageManager.match(/^npm@(.+)$/)
if (!match) {
  console.error('根 package.json#packageManager 必须精确声明 npm@<version>')
  process.exit(1)
}
process.stdout.write(match[1])
NODE
)"

# 基线是下限：等于或高于基线都接受，避免升级 Node/npm 后反而卡住门禁。
if ! version_ge "${ACTUAL_NODE}" "${EXPECTED_NODE}"; then
  echo "Node 版本低于基线: expected >= v${EXPECTED_NODE}, actual ${ACTUAL_NODE:-missing}. 请执行 nvm use ${EXPECTED_NODE}（或更高版本）" >&2
  exit 1
fi

if ! version_ge "${ACTUAL_NPM}" "${EXPECTED_NPM}"; then
  echo "npm 版本低于基线: expected >= ${EXPECTED_NPM}, actual ${ACTUAL_NPM:-missing}. 请使用 Node ${EXPECTED_NODE} 自带 npm 或更高版本" >&2
  exit 1
fi

echo "Node/npm baseline OK: ${ACTUAL_NODE} / npm ${ACTUAL_NPM}（基线 >= v${EXPECTED_NODE} / npm ${EXPECTED_NPM}）"
