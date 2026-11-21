#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: check-package-structure.sh [--scan-root PATH] [--boundaries FILE] [--allowlist FILE]

Scans the first-wave governed scopes for package structure violations.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SCAN_ROOT="$REPO_ROOT"
BOUNDARIES_FILE="$SCRIPT_DIR/package-structure-boundaries.tsv"
ALLOWLIST_FILE="$SCRIPT_DIR/package-structure-allowlist.tsv"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --scan-root)
      SCAN_ROOT="$2"
      shift 2
      ;;
    --boundaries)
      BOUNDARIES_FILE="$2"
      shift 2
      ;;
    --allowlist)
      ALLOWLIST_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ ! -f "$BOUNDARIES_FILE" ]; then
  echo "boundaries file not found: $BOUNDARIES_FILE" >&2
  exit 2
fi

if [ ! -f "$ALLOWLIST_FILE" ]; then
  echo "allowlist file not found: $ALLOWLIST_FILE" >&2
  exit 2
fi

trim_scan_root() {
  local path="$1"
  path="${path#"$SCAN_ROOT"/}"
  printf '%s\n' "$path"
}

read_package_name() {
  local file="$1"
  awk '
    $1 == "package" {
      gsub(/;/, "", $2);
      print $2;
      exit;
    }
  ' "$file"
}

is_allowlisted() {
  local rule_id="$1"
  local relative_path="$2"
  local allow_rule=""
  local scope_glob=""
  local owner=""
  local exit_condition=""
  local note=""

  while IFS=$'\t' read -r allow_rule scope_glob owner exit_condition note; do
    if [[ -z "$allow_rule" || "$allow_rule" == \#* ]]; then
      continue
    fi
    if [[ "$allow_rule" == "$rule_id" && "$relative_path" == $scope_glob ]]; then
      return 0
    fi
  done < "$ALLOWLIST_FILE"

  return 1
}

violation_message_for() {
  local rule_id="$1"
  local package_name="$2"
  local expected_prefix="$3"
  local file="$4"
  local base_name
  base_name="$(basename "$file" .java)"

  case "$rule_id" in
    PKG101|PKG102)
      if [[ "$package_name" != "$expected_prefix" && "$package_name" != "$expected_prefix".* ]]; then
        printf '%s expected package root %s but found %s\n' "$rule_id" "$expected_prefix" "$package_name"
      fi
      ;;
    PKG001)
      local segment
      IFS='.' read -r -a segments <<< "$package_name"
      for segment in "${segments[@]}"; do
        case "$segment" in
          controll|metedata|contextwrapper|req)
            printf '%s banned package segment "%s" found in %s\n' "$rule_id" "$segment" "$package_name"
            return
            ;;
        esac
      done
      ;;
    PKG201)
      if [[ ".$package_name." == *".model."* ]] && [[ "$base_name" =~ (DTO|Request|Response|Entity)$ ]]; then
        printf '%s transport or persistence suffix leaked into model package via %s\n' "$rule_id" "$base_name"
      fi
      ;;
    *)
      printf 'unknown rule id %s\n' "$rule_id"
      ;;
  esac
}

violations=0
scopes=0
allowlisted=0
boundary_rule=""
scope_root=""
expected_prefix=""
owner=""
note=""

while IFS=$'\t' read -r boundary_rule scope_root expected_prefix owner note; do
  if [[ -z "$boundary_rule" || "$boundary_rule" == \#* ]]; then
    continue
  fi

  scopes=$((scopes + 1))
  if [ ! -d "$SCAN_ROOT/$scope_root" ]; then
    continue
  fi

  while IFS= read -r file; do
    package_name="$(read_package_name "$file")"
    [ -n "$package_name" ] || continue

    relative_path="$(trim_scan_root "$file")"
    message="$(violation_message_for "$boundary_rule" "$package_name" "$expected_prefix" "$file")"
    if [ -z "$message" ]; then
      continue
    fi

    if is_allowlisted "$boundary_rule" "$relative_path"; then
      allowlisted=$((allowlisted + 1))
      continue
    fi

    violations=$((violations + 1))
    printf '%s\t%s\t%s\n' "$boundary_rule" "$relative_path" "$message"
  done < <(find "$SCAN_ROOT/$scope_root" -type f -name '*.java' | sort)
done < "$BOUNDARIES_FILE"

printf 'check-package-structure: %d violation(s), %d allowlisted item(s), %d boundary scope(s)\n' \
  "$violations" "$allowlisted" "$scopes"

if [ "$violations" -ne 0 ]; then
  exit 1
fi
