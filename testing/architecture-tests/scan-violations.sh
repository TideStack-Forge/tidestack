#!/bin/bash
# 扫描当前架构违规并生成 allowlist
#
# 用途：
# 1. 初始化 allowlist（首次运行）
# 2. 验证 allowlist 是否完整（CI 中运行）
# 3. 生成违规报告（定期审查）
#
# 使用方法：
#   ./scan-violations.sh [--update-allowlist] [--yes]
#
# 参数：
#   --update-allowlist: 更新 allowlist 文件（谨慎使用，需要审批）
#   --yes: 跳过交互确认（仅用于自动化/已审批场景）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALLOWLIST_FILE="$PROJECT_ROOT/testing/architecture-tests/src/test/resources/architecture-allowlist.tsv"
REPORT_FILE="$PROJECT_ROOT/tmp/testing-evidence/architecture-violations-$(date +%Y%m%d-%H%M%S).txt"

UPDATE_ALLOWLIST=false
AUTO_CONFIRM=false

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --update-allowlist)
            UPDATE_ALLOWLIST=true
            shift
            ;;
        --yes)
            AUTO_CONFIRM=true
            shift
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "=== Ouroboros 架构违规扫描 ==="
echo "项目根目录: $PROJECT_ROOT"
echo "Allowlist 文件: $ALLOWLIST_FILE"
echo "报告文件: $REPORT_FILE"
echo ""

# 创建报告目录
mkdir -p "$(dirname "$REPORT_FILE")"

# 运行 ArchUnit 测试并捕获输出
echo "运行 ArchUnit 测试..."
cd "$PROJECT_ROOT"

# 完整编译后从根 reactor 运行，前置编译失败必须中止扫描。
if ! PRECOMPILE_OUTPUT=$(mvn -DskipTests test-compile 2>&1); then
    echo "$PRECOMPILE_OUTPUT" > "$REPORT_FILE"
    echo "架构扫描前置编译失败，报告：$REPORT_FILE"
    exit 1
fi
# 使用当前 CI 正式入口，收集 ratchet 违规输出
TEST_OUTPUT=$(mvn -pl testing/architecture-tests -am test -Dtest=ArchitectureTestWithAllowlist -Dsurefire.failIfNoSpecifiedTests=false 2>&1 || true)

# 解析违规
{
    echo "=== PRECOMPILE (mvn -DskipTests test-compile) ==="
    echo "$PRECOMPILE_OUTPUT"
    echo ""
    echo "=== ARCHITECTURE TEST (root reactor: ArchitectureTestWithAllowlist) ==="
    echo "$TEST_OUTPUT"
} > "$REPORT_FILE"

# 统计违规数量
LAYER_VIOLATIONS=$(echo "$TEST_OUTPUT" | grep -c "P0-3" || true)
CYCLE_VIOLATIONS=$(echo "$TEST_OUTPUT" | grep -c "P1-14" || true)
EXCEPTION_VIOLATIONS=$(echo "$TEST_OUTPUT" | grep -c "P1-8" || true)
AUTOCONFIG_VIOLATIONS=$(echo "$TEST_OUTPUT" | grep -c "P0-7" || true)
SECURITY_VIOLATIONS=$(echo "$TEST_OUTPUT" | grep -c "P1-12" || true)

TOTAL_VIOLATIONS=$((LAYER_VIOLATIONS + CYCLE_VIOLATIONS + EXCEPTION_VIOLATIONS + AUTOCONFIG_VIOLATIONS + SECURITY_VIOLATIONS))

echo ""
echo "=== 违规统计 ==="
echo "P0-3 跨层依赖: $LAYER_VIOLATIONS"
echo "P1-14 循环依赖: $CYCLE_VIOLATIONS"
echo "P1-8 异常体系: $EXCEPTION_VIOLATIONS"
echo "P0-7 自动配置: $AUTOCONFIG_VIOLATIONS"
echo "P1-12 安全检查: $SECURITY_VIOLATIONS"
echo "总计: $TOTAL_VIOLATIONS"
echo ""

if [ "$UPDATE_ALLOWLIST" = true ]; then
    echo "⚠️  更新 allowlist 模式"
    echo ""
    echo "警告：此操作将更新 allowlist 文件，允许当前所有违规。"
    echo "这应该只在以下情况下使用："
    echo "  1. 首次初始化 allowlist"
    echo "  2. 经过架构团队审批的批量豁免"
    echo ""
    if [ "$AUTO_CONFIRM" = true ]; then
        confirm="yes"
    else
        read -p "确认更新 allowlist？(yes/no): " confirm
    fi
    
    if [ "$confirm" != "yes" ]; then
        echo "取消更新"
        exit 1
    fi
    
    # 备份现有 allowlist
    if [ -f "$ALLOWLIST_FILE" ]; then
        cp "$ALLOWLIST_FILE" "$ALLOWLIST_FILE.backup.$(date +%Y%m%d-%H%M%S)"
        echo "已备份现有 allowlist"
    fi
    
    python3 - "$ALLOWLIST_FILE" "$REPORT_FILE" <<'PY'
from pathlib import Path
import sys

allowlist_path = Path(sys.argv[1])
report_path = Path(sys.argv[2])

lines = report_path.read_text(encoding='utf-8', errors='replace').splitlines()
entries = []
seen = set()
current_rule = None
capture = False
in_architecture_test_section = False

for line in lines:
    stripped = line.strip()

    if stripped.startswith("=== ARCHITECTURE TEST"):
        in_architecture_test_section = True
        current_rule = None
        capture = False
        continue

    if not in_architecture_test_section:
        continue

    if stripped.startswith("P1-14 module dependencies form cycles;"):
        current_rule = "P1-14"
        capture = True
        continue

    if "检测到新的违规（不在 allowlist 中）" in stripped:
        current_rule = stripped.split(" ", 1)[0]
        capture = True
        continue

    if capture:
        if stripped.startswith("修复指引详见") or stripped.startswith("at ") or stripped.startswith("[ERROR]"):
            capture = False
            continue

        if line.startswith("  - "):
            violation = line[4:].strip()
            key = (current_rule, violation)
            if current_rule and violation and key not in seen:
                seen.add(key)
                if current_rule == "P1-14":
                    source, target = violation.split(" -> ", 1)
                    entries.append((current_rule, "module_cycle_edge", source, target, "reviewed module dependency", "@arch-team", "2026-12-31"))
                else:
                    entries.append((current_rule, "baseline_violation", violation, "", "2026-04-19 baseline freeze", "@arch-team", "2026-06-30"))

header = [
    "# Ouroboros 架构约束 Allowlist",
    f"# 自动生成于: baseline normalization {Path(report_path).name}",
    "# 说明：当前存量违规基线；新代码不得引入新的违规",
    "rule_id\tviolation_type\tsource\ttarget\treason\towner\texit_date",
]

body = ["\t".join(entry) for entry in entries]
allowlist_path.write_text("\n".join(header + body) + "\n", encoding='utf-8')
print(f"Generated {len(entries)} allowlist entries -> {allowlist_path}")
PY
    
    echo "✅ Allowlist 已更新"
else
    echo "报告已生成: $REPORT_FILE"
    echo ""
    echo "如需更新 allowlist，请运行："
    echo "  $0 --update-allowlist"
fi

exit 0
