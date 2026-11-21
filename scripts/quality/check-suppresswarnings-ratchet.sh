#!/bin/bash
# Ouroboros @SuppressWarnings Ratchet 检查脚本
#
# 功能：检查当前 @SuppressWarnings(unchecked|rawtypes) 数量是否超过 allowlist 基线
# 用法：scripts/quality/check-suppresswarnings-ratchet.sh
# 退出码：0=通过，1=失败（新增违规）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALLOWLIST="$SCRIPT_DIR/suppresswarnings-allowlist.tsv"

echo "🔍 检查 @SuppressWarnings(unchecked|rawtypes) Ratchet..."

# 统计当前 unchecked/rawtypes 数量（排除 worktrees 和 target）
CURRENT_COUNT=$(grep -r -E '@SuppressWarnings\([^)]*"(unchecked|rawtypes)"'   --include="*.java"   --exclude-dir=".worktrees"   --exclude-dir="target"   --exclude-dir="node_modules"   "$PROJECT_ROOT" 2>/dev/null | wc -l | tr -d ' ')

# 从 allowlist 读取 unchecked/rawtypes 基线（跳过注释和表头）
BASELINE_COUNT=$(awk -F '\t' '!/^#/ && $1 != "file_path" && NF > 0 && ($3 == "unchecked" || $3 == "rawtypes") { c++ } END { print c + 0 }' "$ALLOWLIST")

# 如果 allowlist 为空，使用 Task 0.3 scoped 基线（2026-04-18）
if [ "$BASELINE_COUNT" -eq 0 ]; then
  BASELINE_COUNT=379
  echo "⚠️  Allowlist 为空，使用 Task 0.3 scoped 基线: $BASELINE_COUNT"
fi

echo "📊 当前数量: $CURRENT_COUNT"
echo "📊 基线数量: $BASELINE_COUNT"

if [ "$CURRENT_COUNT" -gt "$BASELINE_COUNT" ]; then
  DIFF=$((CURRENT_COUNT - BASELINE_COUNT))
  echo "❌ Ratchet 检查失败：新增 $DIFF 个 @SuppressWarnings(unchecked|rawtypes)"
  echo ""
  echo "🚫 不允许新增 @SuppressWarnings(unchecked|rawtypes)！"
  echo "   请移除新增抑制或修复根本的泛型类型问题。"
  echo ""
  echo "   如果确实需要新增（极少数情况），请："
  echo "   1. 在代码审查中充分说明理由"
  echo "   2. 更新 allowlist: $ALLOWLIST"
  echo "   3. 指定 owner 和 exit_date"
  exit 1
elif [ "$CURRENT_COUNT" -lt "$BASELINE_COUNT" ]; then
  DIFF=$((BASELINE_COUNT - CURRENT_COUNT))
  echo "✅ Ratchet 检查通过：减少了 $DIFF 个 @SuppressWarnings(unchecked|rawtypes)！"
  echo "   请更新 allowlist 以固化改进成果。"
  exit 0
else
  echo "✅ Ratchet 检查通过：数量未变化"
  exit 0
fi
