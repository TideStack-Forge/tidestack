#!/bin/bash
# P1-10: 禁止硬编码配置
# 检测硬编码的URL、密码、密钥等配置
# 基于历史错误频率：配置错误占29次fix（最高频）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALLOWLIST_FILE="$SCRIPT_DIR/hardcoded-config-allowlist.tsv"

echo "=========================================="
echo "P1-10: 检查硬编码配置"
echo "=========================================="
echo ""

# 检查目录
CHECK_DIRS=(
  "ability"
  "app-module"
  "dev-module"
  "platform"
  "security"
)

# 硬编码模式（排除测试文件和配置文件）
# 说明：http(s) 模式不惩罚"先声明字段常量、再在逻辑中引用"的写法（见 is_url_constant_declaration）；
#       厂商端点常量因此无需登记 allowlist，只有把 URL 直接嵌进方法体/表达式才算违规。
PATTERNS=(
  # URL 硬编码
  'jdbc:mysql://[^$]'
  'jdbc:postgresql://[^$]'
  'http://[^$]'
  'https://[^$]'
  # 密码/密钥硬编码
  'password\s*=\s*"[^"]+"'
  "password\\s*=\\s*'[^']+'"
  'secret\s*=\s*"[^"]+"'
  'apiKey\s*=\s*"[^"]+"'
  'accessKey\s*=\s*"[^"]+"'
  # 端口硬编码（排除常见端口）
  ':[0-9]{4,5}[^0-9]'
)

VIOLATIONS=0
TOTAL_FILES=0

cd "$PROJECT_ROOT"

is_allowlisted() {
  local relative_path="$1"
  local matched_text="$2"
  local allow_path=""
  local allow_text=""
  local owner=""
  local exit_condition=""
  local note=""

  [ -f "$ALLOWLIST_FILE" ] || return 1

  while IFS=$'\t' read -r allow_path allow_text owner exit_condition note; do
    if [[ -z "$allow_path" || "$allow_path" == \#* ]]; then
      continue
    fi
    if [[ "$relative_path" == "$allow_path" && "$matched_text" == *"$allow_text"* ]]; then
      return 0
    fi
  done < "$ALLOWLIST_FILE"

  return 1
}

# URL 常量定义判定：先声明为常量（字段，含跨行初始化）再在逻辑代码中引用，不算硬编码配置；
# 直接嵌在方法体/表达式里的 URL（例如 `return "https://..."`、方法实参、字面量局部变量）仍算违规。
# 仅用于 http(s) 模式；端口、密码等模式不受影响。
is_url_constant_declaration() {
  local file="$1"
  local lineno="$2"
  local text="$3"
  local const_decl='(static[[:space:]]+final|final[[:space:]]+static)'
  # 常量命名（全大写，可带下划线/数字）：覆盖接口常量等无修饰符声明
  local const_name='[A-Z][A-Z0-9_]{2,}'

  # 同一行完成的字段常量声明：`[public] static final String X = "https://..."`、
  # 或按常量命名约定的 `X = "https://..."`（接口隐式 static final）
  if printf '%s' "$text" | grep -Eq "${const_decl}.*=[^=]*https?://" \
    || printf '%s' "$text" | grep -Eq "\b${const_name}[[:space:]]*=[^=]*https?://"; then
    return 0
  fi

  # 跨行声明的续行：本行只有字符串字面量（可带 + 或 ;），且上一行是常量声明的 `... =` 结尾
  local trimmed prev
  trimmed="$(printf '%s' "$text" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  if printf '%s' "$trimmed" | grep -Eq '^"https?://[^"]*"[[:space:]]*[+;]?$'; then
    prev="$(sed -n "$((lineno - 1))p" "$file" 2>/dev/null || true)"
    if printf '%s' "$prev" | grep -Eq "${const_decl}.*=[[:space:]]*$" \
      || printf '%s' "$prev" | grep -Eq "\b${const_name}[[:space:]]*=[[:space:]]*$"; then
      return 0
    fi
  fi

  return 1
}

# URL 例外语境（仅 http(s) 模式）：注释里的文档链接、只有 scheme 的字面量、回环/本机地址。
# 这些不是可配置的外部端点，不应要求提常量。
is_url_exempt_context() {
  local text="$1"
  local url before host

  url="$(printf '%s' "$text" | grep -Eo 'https?://[^[:space:]"]*' | head -1 || true)"
  [ -n "$url" ] || return 1

  # 注释：本行 URL 之前已出现 `//`，或整行以 `//`、`*`、`/*` 开头（javadoc）
  before="${text%%"$url"*}"
  case "$before" in
    *//*) return 0 ;;
  esac
  case "$(printf '%s' "$before" | sed -E 's/^[[:space:]]+//')" in
    '//'*|'*'*|'/*'*) return 0 ;;
  esac

  # 只有 scheme 的字面量：`"https://"`、`"http://" + host`
  case "$url" in
    'http://'|'https://') return 0 ;;
  esac

  # 回环/本机地址：运行时构造，不是部署配置
  host="${url#*://}"
  host="${host%%[:/]*}"
  case "$host" in
    127.0.0.1|localhost|0.0.0.0|'[::1]'|::1) return 0 ;;
  esac

  return 1
}

for dir in "${CHECK_DIRS[@]}"; do
  if [ ! -d "$dir" ]; then
    echo "⚠️  目录不存在: $dir"
    continue
  fi
  
  echo "检查目录: $dir/"
  
  for pattern in "${PATTERNS[@]}"; do
    is_url_pattern=0
    case "$pattern" in
      'http://'*|'https://'*) is_url_pattern=1 ;;
    esac

    # 搜索 Java 文件（排除测试文件）
    matches=$(grep -rn --include="*.java" \
      --exclude-dir=test \
      --exclude-dir=target \
      -E "$pattern" \
      "$dir" 2>/dev/null || true)
    
    if [ -n "$matches" ]; then
      printed_header=0
      while IFS= read -r line; do
        relative_path="${line%%:*}"
        rest="${line#*:}"
        lineno="${rest%%:*}"
        matched_text="${rest#*:}"
        if is_allowlisted "$relative_path" "$matched_text"; then
          continue
        fi
        if [ "$is_url_pattern" -eq 1 ] \
          && { is_url_constant_declaration "$relative_path" "$lineno" "$matched_text" \
               || is_url_exempt_context "$matched_text"; }; then
          continue
        fi
        if [ "$printed_header" -eq 0 ]; then
          echo ""
          echo "❌ 发现硬编码配置（模式: $pattern）:"
          printed_header=1
        fi
        VIOLATIONS=$((VIOLATIONS + 1))
        echo "  $line"
      done <<< "$matches"
    fi
  done
done

echo ""
echo "=========================================="
if [ $VIOLATIONS -eq 0 ]; then
  echo "✅ 通过：未发现硬编码配置"
  echo "=========================================="
  exit 0
else
  echo "❌ 失败：发现 $VIOLATIONS 处硬编码配置"
  echo ""
  echo "修复建议："
  echo "1. URL/端口：使用 application.yml 或 @Value 注入"
  echo "2. 密码/密钥：使用环境变量或配置中心"
  echo "3. 参考：ouroboros-configuration 模块"
  echo ""
  echo "详见 DESIGN.md 和 docs/design-docs/cross-cutting/configuration.md"
  echo "=========================================="
  exit 1
fi
