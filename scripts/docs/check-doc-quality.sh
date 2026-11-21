#!/bin/bash
# Ouroboros 文档质量检查脚本（文档园丁）
#
# 功能：
# 1. 新鲜度与内容一致性统计（链接与锚点检查由 scripts/docs/check-links.sh 负责，本脚本不做）
# 2. 新鲜度检查（基于 Git 历史）
# 3. 内容一致性检查（worktree 路径、过时引用、标记）
#
# 用法：scripts/docs/check-doc-quality.sh
# 输出：target/doc-quality/doc-quality-YYYY-MM-DD.md（构建产物目录，已被 .gitignore 忽略）
#
# 说明：报告写入 target/ 而非 docs/，避免 CI 运行检查时污染工作树。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DATE=$(date +%Y-%m-%d)
REPORT_DIR="$PROJECT_ROOT/target/doc-quality"
REPORT_FILE="$REPORT_DIR/doc-quality-$REPORT_DATE.md"

mkdir -p "$REPORT_DIR"
SCAN_DIR=$(mktemp -d "$REPORT_DIR/scan.XXXXXX")
trap 'rm -rf "$SCAN_DIR"' EXIT
DOC_LIST="$SCAN_DIR/current-docs.txt"
RECENT_DOCS="$SCAN_DIR/recent-docs.txt"
GREP_EXCLUDES=(--exclude-dir=handbook --exclude-dir=node_modules --exclude-dir=target)
LEGACY_PATTERN='\]\((\./|\.\./|/)*(docs/)?design-docs/'
cd "$PROJECT_ROOT"
find docs -type d \( -name handbook -o -name node_modules -o -name target \) -prune -o -type f -name '*.md' -print | sort > "$DOC_LIST"

echo "🔍 开始文档质量检查..."
echo "📅 日期: $REPORT_DATE"
echo "📁 项目根目录: $PROJECT_ROOT"
echo ""

# 初始化报告
cat > "$REPORT_FILE" << 'EOF'
# 文档质量检查报告

**检查日期**: REPORT_DATE_PLACEHOLDER
**检查范围**: docs/ 目录（递归，排除 handbook、node_modules 和 target）

Git 更新时间仅用于安排人工核查，不证明内容过期或正确。
**检查工具**: 文档园丁自动化脚本

---

## 执行摘要

EOF

# 替换日期占位符
sed -i.bak "s/REPORT_DATE_PLACEHOLDER/$REPORT_DATE/" "$REPORT_FILE" && rm "$REPORT_FILE.bak"

# 1. 统计文档数量
echo "📊 统计文档数量..."
TOTAL_DOCS=$(wc -l < "$DOC_LIST" | tr -d ' ')
echo "   总文档数: $TOTAL_DOCS"

# 2. 检查新鲜度（Git 历史）
echo "📅 检查文档新鲜度..."
git log --since="30 days ago" --name-only --pretty=format: -- docs > "$SCAN_DIR/recent-history.txt"
sort -u "$SCAN_DIR/recent-history.txt" > "$SCAN_DIR/recent-paths.txt"
comm -12 "$DOC_LIST" "$SCAN_DIR/recent-paths.txt" > "$RECENT_DOCS"
STALE_DOCS=$(wc -l < "$RECENT_DOCS" | tr -d ' ')
STALE_COUNT=$((TOTAL_DOCS - STALE_DOCS))
echo "   30 天内更新: $STALE_DOCS"
echo "   超过 30 天: $STALE_COUNT"

# 3. 检查链接（简化版：检查常见死链模式）
echo "🔗 检查文档链接..."
BROKEN_LINKS=0
WORKTREE_REFS=0
LEGACY_REFS=0

# 检查 worktree 绝对路径
WORKTREE_REFS=$(cd "$PROJECT_ROOT" && grep -r "${GREP_EXCLUDES[@]}" "/.worktrees/" --include="*.md" docs 2>/dev/null | wc -l | tr -d ' ' || true)
echo "   Worktree 绝对路径: $WORKTREE_REFS"

# 检查迁移前的设计目录；当前规范正文位于 docs/design/。
LEGACY_REFS=$(cd "$PROJECT_ROOT" && grep -rE "${GREP_EXCLUDES[@]}" "$LEGACY_PATTERN" --include="*.md" docs 2>/dev/null | wc -l | tr -d ' ' || true)
echo "   过时路径引用: $LEGACY_REFS"

# 4. 检查标记
echo "🏷️  检查文档标记..."
TODO_MARKERS=$(cd "$PROJECT_ROOT" && grep -r "${GREP_EXCLUDES[@]}" "TODO\|OUTDATED\|DEPRECATED" --include="*.md" docs 2>/dev/null | wc -l | tr -d ' ' || true)
echo "   TODO/OUTDATED/DEPRECATED: $TODO_MARKERS"
RECENT_PERCENT=0
STALE_PERCENT=0
if [ "$TOTAL_DOCS" -gt 0 ]; then
  RECENT_PERCENT=$((STALE_DOCS * 100 / TOTAL_DOCS))
  STALE_PERCENT=$((STALE_COUNT * 100 / TOTAL_DOCS))
fi

# 5. 生成报告
cat >> "$REPORT_FILE" << EOF

**总文档数**: $TOTAL_DOCS  
**30 天内更新**: $STALE_DOCS ($RECENT_PERCENT%)<br>
**超过 30 天**: $STALE_COUNT ($STALE_PERCENT%)<br>
**Worktree 绝对路径**: $WORKTREE_REFS  
**过时路径引用**: $LEGACY_REFS  
**TODO/OUTDATED/DEPRECATED**: $TODO_MARKERS

---

## 详细检查结果

### 1. 新鲜度检查

EOF

# 列出超期文档（如果有）
if [ "$STALE_COUNT" -gt 0 ]; then
  echo "#### 超过 30 天未更新的文档" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  cd "$PROJECT_ROOT"
  while IFS= read -r file; do
    if ! grep -Fxq "$file" "$RECENT_DOCS"; then
      LAST_COMMIT=$(git log -1 --format="%ai" -- "$file" 2>/dev/null || echo "未知")
      echo "- \`$file\` (最后更新: $LAST_COMMIT)" >> "$REPORT_FILE"
    fi
  done < "$DOC_LIST"
else
  echo "✅ 所有文档都在 30 天内更新过。" >> "$REPORT_FILE"
fi

echo "" >> "$REPORT_FILE"

### 2. 内容一致性检查
cat >> "$REPORT_FILE" << EOF

### 2. 内容一致性检查

EOF

if [ "$WORKTREE_REFS" -gt 0 ]; then
  echo "#### Worktree 绝对路径引用（需修复）" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  (cd "$PROJECT_ROOT" && grep -rn "${GREP_EXCLUDES[@]}" "/.worktrees/" --include="*.md" docs 2>/dev/null | head -20) >> "$REPORT_FILE" || true
  echo "" >> "$REPORT_FILE"
fi

if [ "$LEGACY_REFS" -gt 0 ]; then
  echo "#### 过时路径引用（需修复）" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  (cd "$PROJECT_ROOT" && grep -rnE "${GREP_EXCLUDES[@]}" "$LEGACY_PATTERN" --include="*.md" docs 2>/dev/null | head -20) >> "$REPORT_FILE" || true
  echo "" >> "$REPORT_FILE"
fi

if [ "$TODO_MARKERS" -gt 0 ]; then
  echo "#### TODO/OUTDATED/DEPRECATED 标记" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  (cd "$PROJECT_ROOT" && grep -rn "${GREP_EXCLUDES[@]}" "TODO\|OUTDATED\|DEPRECATED" --include="*.md" docs 2>/dev/null | head -20) >> "$REPORT_FILE" || true
  echo "" >> "$REPORT_FILE"
fi

# 6. 生成修复建议
cat >> "$REPORT_FILE" << EOF

---

## 修复建议

### 优先级 P0（立即修复）

EOF

if [ "$WORKTREE_REFS" -gt 0 ]; then
  echo "- [ ] 替换所有 Worktree 绝对路径为相对路径（$WORKTREE_REFS 处）" >> "$REPORT_FILE"
fi

if [ "$LEGACY_REFS" -gt 0 ]; then
  echo "- [ ] 更新过时路径引用 \`docs/design-docs/\` → \`docs/design/\`（$LEGACY_REFS 处）" >> "$REPORT_FILE"
fi

cat >> "$REPORT_FILE" << EOF

### 优先级 P1（本周完成）

EOF

if [ "$STALE_COUNT" -gt 5 ]; then
  echo "- [ ] 审查超期文档（$STALE_COUNT 个），确认是否需要更新或归档" >> "$REPORT_FILE"
fi

if [ "$TODO_MARKERS" -gt 10 ]; then
  echo "- [ ] 处理文档中的 TODO/OUTDATED/DEPRECATED 标记（$TODO_MARKERS 处）" >> "$REPORT_FILE"
fi

cat >> "$REPORT_FILE" << EOF

---

## 下次检查

**计划日期**: $(date -v+7d +%Y-%m-%d 2>/dev/null || date -d "+7 days" +%Y-%m-%d 2>/dev/null || echo "一周后")  
**执行方式**: 在目标 checkout 执行 scripts/docs/check-doc-quality.sh

---

**报告生成**: $REPORT_DATE  
**生成工具**: scripts/docs/check-doc-quality.sh
EOF

echo ""
echo "✅ 文档质量检查完成！"
echo "📄 报告已生成: $REPORT_FILE"
echo ""

# 7. 判断是否需要创建 PR
ISSUES_FOUND=$((WORKTREE_REFS + LEGACY_REFS))
if [ "$ISSUES_FOUND" -gt 0 ]; then
  echo "⚠️  发现 $ISSUES_FOUND 个需要修复的问题"
  echo "   建议创建 PR 进行修复"
  exit 1
else
  echo "✅ 未发现需要立即修复的问题"
  exit 0
fi
