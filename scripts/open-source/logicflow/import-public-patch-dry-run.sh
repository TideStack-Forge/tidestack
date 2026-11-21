#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
BOOTSTRAP_MANIFEST="${REPO_ROOT}/scripts/open-source/manifests/logicflow/logicflow-public-repo-bootstrap.yaml"
SYNC_MANIFEST="${REPO_ROOT}/scripts/open-source/manifests/logicflow/logicflow-public-sync-pipeline.yaml"
ARTIFACT_DIR="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline"
REPORT_PATH="${ARTIFACT_DIR}/patch-import-report.md"
CHECK=false
PATCH_FILE=""
PUBLIC_REF=""

usage() {
  cat <<'USAGE'
Usage: import-public-patch-dry-run.sh [--check] [--patch FILE] [--public-ref REF]

Classifies a public LogicFlow patch before maintainers apply it internally.
Public candidate paths produce an apply plan. Internal adapter or generated
metadata paths are blocked and require a separate feature or maintainer review.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK=true
      shift
      ;;
    --patch)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --patch" >&2
        exit 2
      fi
      PATCH_FILE="$2"
      shift 2
      ;;
    --public-ref)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --public-ref" >&2
        exit 2
      fi
      PUBLIC_REF="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "${ARTIFACT_DIR}"

parse_public_targets() {
  awk '
    /^public_modules:/ { in_public = 1; next }
    in_public && /^[a-z_]+:/ { exit }
    in_public && /^[[:space:]]+target:/ { print $2 }
  ' "${BOOTSTRAP_MANIFEST}"
  printf '%s\n' "samples/script-node-standalone"
}

parse_public_module_mappings() {
  awk '
    /^public_modules:/ { in_public = 1; next }
    in_public && /^[a-z_]+:/ { exit }
    in_public && /^[[:space:]]+source:/ { source = $2 }
    in_public && /^[[:space:]]+target:/ {
      target = $2
      if (source != "" && target != "") {
        print target "|" source
      }
      source = ""
      target = ""
    }
  ' "${BOOTSTRAP_MANIFEST}"
}

parse_forbidden_paths() {
  awk '
    /^forbidden_paths:/ { in_forbidden = 1; next }
    in_forbidden && /^[a-z_]+:/ { exit }
    in_forbidden && /^[[:space:]]+- / {
      sub(/^[[:space:]]+- /, "")
      print
    }
  ' "${BOOTSTRAP_MANIFEST}"
}

generated_metadata_path() {
  case "$1" in
    pom.xml|README.md|LICENSE|NOTICE|OPEN_SOURCE_SNAPSHOT.md|.github/*|ouroboros-logicflow-bom/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

path_starts_with_any() {
  local path="$1"
  shift
  local prefix
  for prefix in "$@"; do
    if [[ "${path}" == "${prefix}" || "${path}" == "${prefix}/"* ]]; then
      return 0
    fi
  done
  return 1
}

map_public_path_to_internal_source() {
  local public_path="$1"
  shift
  local mapping target source suffix
  for mapping in "$@"; do
    target="${mapping%%|*}"
    source="${mapping#*|}"
    if [[ "${public_path}" == "${target}" || "${public_path}" == "${target}/"* ]]; then
      suffix="${public_path#${target}}"
      suffix="${suffix#/}"
      if [[ -n "${suffix}" ]]; then
        printf '%s -> %s/%s\n' "${public_path}" "${source}" "${suffix}"
      else
        printf '%s -> %s\n' "${public_path}" "${source}"
      fi
      return 0
    fi
  done
  printf '%s -> generated public surface; maintainer review required before internal apply\n' "${public_path}"
}

extract_patch_paths() {
  local patch_file="$1"
  awk '
    /^diff --git / {
      for (i = 3; i <= NF; i++) {
        path = $i
        sub(/^a\//, "", path)
        sub(/^b\//, "", path)
        if (path != "/dev/null") {
          print path
        }
      }
    }
    /^--- / || /^\+\+\+ / {
      path = $2
      sub(/^a\//, "", path)
      sub(/^b\//, "", path)
      if (path != "/dev/null") {
        print path
      }
    }
  ' "${patch_file}" | LC_ALL=C sort -u
}

extract_ref_paths() {
  local ref="$1"
  git -C "${REPO_ROOT}" diff --name-only "${ref}" | LC_ALL=C sort -u
}

write_report() {
  local status="$1"
  local origin="$2"
  local touched_file="$3"
  local public_file="$4"
  local blocked_file="$5"
  local reason="$6"

  {
    cat <<EOF
---
doc_type: logicflow-public-patch-import-report
roadmap: logicflow-open-source-extraction
feature: 2026-07-26-logicflow-public-sync-pipeline
status: ${status}
---

# LogicFlow Public Patch Import Report

## Origin

- Input: ${origin}
- Network remote access: no
- Patch applied automatically: no

## Touched Paths

EOF
    write_path_list "${touched_file}"
    cat <<EOF

## Public Candidate Apply Plan

EOF
    write_path_list "${public_file}"
    cat <<EOF

## Blocked Paths

EOF
    write_path_list "${blocked_file}"
    cat <<EOF

## Decision

${reason}
EOF
  } > "${REPORT_PATH}"
}

write_path_list() {
  local path_file="$1"
  local path
  if [[ ! -s "${path_file}" ]]; then
    printf '%s\n' "- none"
    return
  fi
  while IFS= read -r path; do
    printf '%s\n' "- \`${path}\`"
  done < "${path_file}"
}

classify_paths() {
  local origin="$1"
  local paths_file="$2"
  local public_targets_file="$3"
  local forbidden_paths_file="$4"
  local touched_file="${ARTIFACT_DIR}/patch-touched-paths.txt"
  local public_file="${ARTIFACT_DIR}/patch-public-candidate-paths.txt"
  local blocked_file="${ARTIFACT_DIR}/patch-blocked-paths.txt"
  local mappings_file="${ARTIFACT_DIR}/patch-public-module-mappings.txt"
  : > "${touched_file}"
  : > "${public_file}"
  : > "${blocked_file}"
  parse_public_module_mappings > "${mappings_file}"

  local -a public_targets=()
  while IFS= read -r path; do
    [[ -n "${path}" ]] && public_targets+=("${path}")
  done < "${public_targets_file}"

  local -a forbidden_paths=()
  while IFS= read -r path; do
    [[ -n "${path}" ]] && forbidden_paths+=("${path}")
  done < "${forbidden_paths_file}"

  local -a public_mappings=()
  while IFS= read -r mapping; do
    [[ -n "${mapping}" ]] && public_mappings+=("${mapping}")
  done < "${mappings_file}"

  local path
  while IFS= read -r path; do
    [[ -z "${path}" ]] && continue
    printf '%s\n' "${path}" >> "${touched_file}"
    if [[ ${#public_targets[@]} -gt 0 ]] && path_starts_with_any "${path}" "${public_targets[@]}"; then
      map_public_path_to_internal_source "${path}" "${public_mappings[@]}" >> "${public_file}"
    elif [[ ${#forbidden_paths[@]} -gt 0 ]] && path_starts_with_any "${path}" "${forbidden_paths[@]}"; then
      printf '%s\n' "${path}" >> "${blocked_file}"
    elif generated_metadata_path "${path}"; then
      printf '%s\n' "${path}" >> "${blocked_file}"
    else
      printf '%s\n' "${path}" >> "${blocked_file}"
    fi
  done < "${paths_file}"

  if [[ ! -s "${touched_file}" ]]; then
    echo "No changed paths found in public patch input" >&2
    exit 1
  fi

  if [[ -s "${blocked_file}" ]]; then
    write_report "blocked" "${origin}" "${touched_file}" "${public_file}" "${blocked_file}" "Blocked: patch touches internal adapter, generated public metadata, or unknown paths. Open a new feature or maintainer review before applying internally."
    return 3
  fi

  write_report "planned" "${origin}" "${touched_file}" "${public_file}" "${blocked_file}" "Planned: patch only touches public candidate paths. Maintainer may apply this plan to the internal public-candidate sources."
  return 0
}

run_check() {
  local tmp_dir
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-public-patch.XXXXXX")"
  trap "rm -rf '${tmp_dir}'" EXIT

  local public_patch="${tmp_dir}/public-only.patch"
  local adapter_patch="${tmp_dir}/adapter-touch.patch"
  local public_paths="${tmp_dir}/public.paths"
  local adapter_paths="${tmp_dir}/adapter.paths"
  local public_targets="${tmp_dir}/public-targets.txt"
  local forbidden_paths="${tmp_dir}/forbidden-paths.txt"
  parse_public_targets > "${public_targets}"
  parse_forbidden_paths > "${forbidden_paths}"

  cat > "${public_patch}" <<'EOF'
diff --git a/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/PublicPatchFixture.java b/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/PublicPatchFixture.java
new file mode 100644
--- /dev/null
+++ b/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/PublicPatchFixture.java
@@ -0,0 +1 @@
+class PublicPatchFixture {}
EOF

  cat > "${adapter_patch}" <<'EOF'
diff --git a/ouroboros-logicflow-extra-nodes/pom.xml b/ouroboros-logicflow-extra-nodes/pom.xml
--- a/ouroboros-logicflow-extra-nodes/pom.xml
+++ b/ouroboros-logicflow-extra-nodes/pom.xml
@@ -1 +1 @@
-old
+new
EOF

  extract_patch_paths "${public_patch}" > "${public_paths}"
  classify_paths "check:public-only-patch" "${public_paths}" "${public_targets}" "${forbidden_paths}"
  if ! grep -q "status: planned" "${REPORT_PATH}"; then
    echo "Public-only patch check did not produce a planned report" >&2
    exit 1
  fi
  if ! grep -q "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/PublicPatchFixture.java" "${REPORT_PATH}"; then
    echo "Public-only patch check did not map the public path to the internal public-candidate source" >&2
    exit 1
  fi

  extract_patch_paths "${adapter_patch}" > "${adapter_paths}"
  if classify_paths "check:adapter-touch-patch" "${adapter_paths}" "${public_targets}" "${forbidden_paths}"; then
    echo "Adapter-touch patch check was not blocked" >&2
    exit 1
  fi
  if ! grep -q "ouroboros-logicflow-extra-nodes/pom.xml" "${REPORT_PATH}"; then
    echo "Adapter-touch block report did not list the touched adapter path" >&2
    exit 1
  fi

  cat >> "${REPORT_PATH}" <<'EOF'

## Check Summary

- Public-only fixture patch produced an apply plan mapped to the internal public-candidate source.
- Adapter-touch fixture patch was blocked and listed the touched path.
EOF
}

if [[ ! -f "${SYNC_MANIFEST}" ]]; then
  echo "Missing sync manifest: ${SYNC_MANIFEST}" >&2
  exit 1
fi
if [[ ! -f "${BOOTSTRAP_MANIFEST}" ]]; then
  echo "Missing bootstrap manifest: ${BOOTSTRAP_MANIFEST}" >&2
  exit 1
fi

if [[ "${CHECK}" == true ]]; then
  run_check
  echo "LogicFlow public patch import dry-run check passed: ${REPORT_PATH}"
  exit 0
fi

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-public-patch.XXXXXX")"
trap "rm -rf '${tmp_dir}'" EXIT
paths_file="${tmp_dir}/paths.txt"
public_targets_file="${tmp_dir}/public-targets.txt"
forbidden_paths_file="${tmp_dir}/forbidden-paths.txt"
parse_public_targets > "${public_targets_file}"
parse_forbidden_paths > "${forbidden_paths_file}"

if [[ -n "${PATCH_FILE}" ]]; then
  if [[ "${PATCH_FILE}" != /* ]]; then
    PATCH_FILE="${REPO_ROOT}/${PATCH_FILE}"
  fi
  if [[ ! -f "${PATCH_FILE}" ]]; then
    echo "Missing patch file: ${PATCH_FILE}" >&2
    exit 1
  fi
  extract_patch_paths "${PATCH_FILE}" > "${paths_file}"
  classify_paths "patch:${PATCH_FILE}" "${paths_file}" "${public_targets_file}" "${forbidden_paths_file}"
  echo "LogicFlow public patch import dry-run report: ${REPORT_PATH}"
  exit 0
fi

if [[ -n "${PUBLIC_REF}" ]]; then
  extract_ref_paths "${PUBLIC_REF}" > "${paths_file}"
  classify_paths "public-ref:${PUBLIC_REF}" "${paths_file}" "${public_targets_file}" "${forbidden_paths_file}"
  echo "LogicFlow public patch import dry-run report: ${REPORT_PATH}"
  exit 0
fi

echo "Expected --patch, --public-ref, or --check" >&2
usage >&2
exit 2
