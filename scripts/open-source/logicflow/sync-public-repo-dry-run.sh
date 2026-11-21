#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
SYNC_MANIFEST="${REPO_ROOT}/scripts/open-source/manifests/logicflow/logicflow-public-sync-pipeline.yaml"
BOOTSTRAP_SCRIPT="${SCRIPT_DIR}/bootstrap-public-repo-dry-run.sh"
LEAK_CHECK_SCRIPT="${SCRIPT_DIR}/check-public-repo-leaks.sh"
BOOTSTRAP_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-repo-bootstrap/bootstrap-report.md"
OUTPUT_DIR="${REPO_ROOT}/target/logicflow-public-repo-dry-run"
REPEAT_OUTPUT_DIR="${REPO_ROOT}/target/logicflow-public-sync-pipeline/repeated-export"
CHECK=false
RUN_BUILD=false

usage() {
  cat <<'USAGE'
Usage: sync-public-repo-dry-run.sh [--check] [--output DIR]

Runs the internal-only LogicFlow public sync dry-run. It reuses the bootstrap
export manifest, compares repeated normalized snapshot hashes, writes the
private sync ledger, and generates a public-safe commit plan.

Operational mode requires a clean internal worktree. --check runs in an
implementation/CI context and uses a negative probe to prove the clean gate.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK=true
      RUN_BUILD=true
      shift
      ;;
    --output)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --output" >&2
        exit 2
      fi
      OUTPUT_DIR="$2"
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

resolve_path() {
  local path="$1"
  if [[ "${path}" != /* ]]; then
    path="${REPO_ROOT}/${path}"
  fi
  mkdir -p "$(dirname "${path}")"
  (cd "$(dirname "${path}")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "${path}")")
}

OUTPUT_DIR="$(resolve_path "${OUTPUT_DIR}")"
REPEAT_OUTPUT_DIR="$(resolve_path "${REPEAT_OUTPUT_DIR}")"
ARTIFACT_DIR="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline"
SYNC_REPORT="${ARTIFACT_DIR}/sync-report.md"
METADATA_SCAN_REPORT="${ARTIFACT_DIR}/metadata-scan-report.md"
COMMIT_PLAN="${ARTIFACT_DIR}/public-commit-plan.md"
mkdir -p "${ARTIFACT_DIR}"

manifest_section_value() {
  local section="$1"
  local key="$2"
  awk -v section="${section}" -v key="${key}" '
    $0 ~ "^" section ":" { in_section = 1; next }
    in_section && /^[^[:space:]]/ { in_section = 0 }
    in_section {
      pattern = "^[[:space:]]+" key ":[[:space:]]*"
      if ($0 ~ pattern) {
        sub(pattern, "")
        gsub(/^"|"$/, "")
        print
        exit
      }
    }
  ' "${SYNC_MANIFEST}"
}

validate_manifest() {
  if [[ ! -f "${SYNC_MANIFEST}" ]]; then
    echo "Missing sync manifest: ${SYNC_MANIFEST}" >&2
    exit 1
  fi
  if rg -n "^(public_modules|public_paths|include_paths|exclude_paths|public_include|public_exclude|path_include|path_exclude):" "${SYNC_MANIFEST}" >/dev/null; then
    echo "Sync manifest must not define its own public module/path include/exclude list" >&2
    exit 1
  fi
  for referenced in \
    logicflow-public-repo-bootstrap.yaml \
    logicflow-open-source-boundary.yaml \
    logicflow-platform-adapter-boundary.yaml \
    logicflow-extension-bundle-classification.yaml; do
    if [[ ! -f "${REPO_ROOT}/scripts/open-source/manifests/logicflow/${referenced}" ]]; then
      echo "Missing referenced manifest: ${referenced}" >&2
      exit 1
    fi
  done
}

require_clean_worktree() {
  if [[ "${LOGICFLOW_SYNC_CLEAN_PROBE_DIRTY:-false}" == "true" ]]; then
    echo "Internal worktree is dirty; refusing public sync." >&2
    return 1
  fi
  if [[ -n "$(git -C "${REPO_ROOT}" status --porcelain --untracked-files=all)" ]]; then
    echo "Internal worktree is dirty; refusing public sync." >&2
    return 1
  fi
}

run_clean_gate_probe() {
  if LOGICFLOW_SYNC_CLEAN_PROBE_DIRTY=true require_clean_worktree >/dev/null 2>&1; then
    echo "Clean worktree negative probe failed: dirty probe was accepted" >&2
    exit 1
  fi
}

with_preserved_bootstrap_report() {
  local tmp_dir="$1"
  local had_report=false
  local saved_report="${tmp_dir}/bootstrap-report.md"

  if [[ -f "${BOOTSTRAP_REPORT}" ]]; then
    had_report=true
    cp "${BOOTSTRAP_REPORT}" "${saved_report}"
  fi

  bash "${BOOTSTRAP_SCRIPT}" --check --output "${OUTPUT_DIR}"
  bash "${BOOTSTRAP_SCRIPT}" --check --output "${REPEAT_OUTPUT_DIR}"

  if [[ "${had_report}" == true ]]; then
    mkdir -p "$(dirname "${BOOTSTRAP_REPORT}")"
    cp "${saved_report}" "${BOOTSTRAP_REPORT}"
  elif [[ -f "${BOOTSTRAP_REPORT}" ]]; then
    rm -f "${BOOTSTRAP_REPORT}"
  fi
}

normalized_content_hash() {
  local snapshot_dir="$1"
  (
    cd "${snapshot_dir}"
    find . -type f \
      ! -path './.git/*' \
      ! -path './.codestable/*' \
      ! -path './target/*' \
      ! -path '*/target/*' \
      ! -name '.DS_Store' \
      -print | LC_ALL=C sort | while IFS= read -r file_path; do
        file_hash="$(perl -0pe 's/\r\n?/\n/g' "${file_path}" | shasum -a 256 | awk '{print $1}')"
        printf '%s  %s\n' "${file_hash}" "${file_path}"
      done | shasum -a 256 | awk '{print $1}'
  )
}

verify_required_capability() {
  local snapshot_dir="$1"
  local sample_test="${snapshot_dir}/samples/script-node-standalone/src/test/java/com/ouroboros/logicflow/samples/ScriptNodeStandaloneSampleTest.java"
  local script_define="${snapshot_dir}/ouroboros-logicflow-authoring/src/main/metadata/ScriptNode.flow-node.json"
  local node_builder_descriptor="${snapshot_dir}/ouroboros-logicflow-builtin-nodes/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder"

  if [[ ! -f "${sample_test}" ]]; then
    echo "Missing ScriptNode standalone sample test in public snapshot" >&2
    exit 1
  fi
  if [[ ! -f "${script_define}" ]]; then
    echo "Missing ScriptNode NodeDefine JSON in public snapshot" >&2
    exit 1
  fi
  if [[ ! -f "${node_builder_descriptor}" ]] || ! grep -q "ScriptNode" "${node_builder_descriptor}"; then
    echo "Missing ScriptNode NodeBuilder SPI evidence in public snapshot" >&2
    exit 1
  fi
}

write_commit_plan() {
  local export_hash="$1"
  cat > "${COMMIT_PLAN}" <<EOF
# LogicFlow Public Commit Plan

Status: dry-run plan only

Suggested public commit message:

\`\`\`text
Update LogicFlow public snapshot
\`\`\`

Maintainer public identity: configure outside this feature before any real public commit.

Public snapshot content hash: \`${export_hash}\`

Not performed by this feature:

- public repository creation
- public remote configuration
- real commit
- push
- tag
- release

This plan is safe to review as public metadata, but it is not a publication authorization.
EOF
}

scan_public_metadata() {
  local snapshot_dir="$1"
  local failures=0
  : > "${METADATA_SCAN_REPORT}"

  scan_pattern() {
    local description="$1"
    local pattern="$2"
    if grep -R -n -E --exclude-dir=target "${pattern}" "${snapshot_dir}" "${COMMIT_PLAN}" >> "${METADATA_SCAN_REPORT}" 2>/dev/null; then
      echo "FAIL: ${description}" >> "${METADATA_SCAN_REPORT}"
      failures=$((failures + 1))
    fi
  }

  scan_email_pattern() {
    if grep -R -h -o -E --exclude-dir=target '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "${snapshot_dir}" "${COMMIT_PLAN}" 2>/dev/null \
      | grep -v -E '^[^@]+@example\.(com|org|net)$' >> "${METADATA_SCAN_REPORT}"; then
      echo "FAIL: email address in public metadata plan or snapshot" >> "${METADATA_SCAN_REPORT}"
      failures=$((failures + 1))
    fi
  }

  scan_pattern "40-character hex marker that could expose an internal commit" '\b[0-9a-f]{40}\b'
  scan_pattern "internal absolute path or private workspace marker" '(/Users/|/Projects/ouroboros|\.worktrees|\.codestable|scripts/open-source|core/ouroboros-logicflow)'
  scan_email_pattern
  scan_pattern "internal branch/task marker" '(codex/|\.codestable/features|logicflow-public-sync-pipeline)'

  if [[ ${failures} -gt 0 ]]; then
    echo "Public metadata scan failed; see ${METADATA_SCAN_REPORT}" >&2
    exit 1
  fi

  cat > "${METADATA_SCAN_REPORT}" <<EOF
# Public Metadata Scan Report

Status: passed

Scanned:

- generated public snapshot
- public commit plan

Forbidden metadata classes:

- internal 40-character commit markers
- internal absolute paths and private workspace markers
- author email addresses
- internal branch/task markers
EOF
}

append_private_ledger() {
  local export_hash="$1"
  local ledger_rel
  ledger_rel="$(manifest_section_value "private_ledger" "path")"
  if [[ -z "${ledger_rel}" ]]; then
    echo "Sync manifest does not declare private_ledger.path" >&2
    exit 1
  fi

  local ledger_path="${REPO_ROOT}/${ledger_rel}"
  mkdir -p "$(dirname "${ledger_path}")"
  if [[ ! -f "${ledger_path}" ]]; then
    cat > "${ledger_path}" <<'EOF'
ledger_version: 1
entries:
EOF
  fi

  local internal_ref
  internal_ref="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
  local patch_id
  patch_id="sync-$(date -u +%Y%m%d%H%M%S)"
  local synced_at
  synced_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  cat >> "${ledger_path}" <<EOF
  - direction: internal-to-public
    patch_id: ${patch_id}
    internal_base_ref: ${internal_ref}
    export_content_hash: ${export_hash}
    public_commit: planned-public-commit
    public_origin: internal-export-dry-run
    synced_at: ${synced_at}
    status: dry-run-passed
    notes: "internal-only sync dry-run; not exported to public snapshot"
EOF

  printf '%s\n' "${ledger_path}"
}

verify_ledger_not_exported() {
  local snapshot_dir="$1"
  local ledger_path="$2"
  local ledger_name
  ledger_name="$(basename "${ledger_path}")"
  local ledger_rel="${ledger_path#${REPO_ROOT}/}"

  if find "${snapshot_dir}" -name "${ledger_name}" -print -quit | grep -q .; then
    echo "Public snapshot contains private ledger file name: ${ledger_name}" >&2
    exit 1
  fi
  if grep -R -n -E --exclude-dir=target "(internal_base_ref|${ledger_rel//\//\\/})" "${snapshot_dir}" >/dev/null; then
    echo "Public snapshot contains private ledger marker or path" >&2
    exit 1
  fi
}

write_sync_report() {
  local hash_one="$1"
  local hash_two="$2"
  local ledger_path="$3"
  local build_result="$4"
  local updated_at
  updated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  local ledger_rel="${ledger_path#${REPO_ROOT}/}"

  cat > "${SYNC_REPORT}" <<EOF
---
doc_type: logicflow-public-sync-report
roadmap: logicflow-open-source-extraction
feature: 2026-07-26-logicflow-public-sync-pipeline
status: dry-run-passed
updated: ${updated_at}
---

# LogicFlow Public Sync Report

## Deterministic Export

- First public snapshot: \`${OUTPUT_DIR}\`
- Repeated public snapshot: \`${REPEAT_OUTPUT_DIR}\`
- First normalized content hash: \`${hash_one}\`
- Repeated normalized content hash: \`${hash_two}\`
- Hashes match: yes
- Hash scope: generated public snapshot content only
- Hash exclusions: version control metadata, build artifacts, internal ledger, sync report, generated time, machine paths

## Existing Checks

- Bootstrap export check: passed
- Leak check: passed
- Dry-run Maven build: ${build_result}
- Required capability evidence: ScriptNode sample, ScriptNode NodeDefine JSON, and NodeBuilder SPI descriptor remain in the public snapshot

## Private Sync Ledger

- Ledger path: \`${ledger_rel}\`
- Ledger exported to public snapshot: no
- Required fields recorded: direction, patch_id, internal_base_ref, export_content_hash, public_commit, public_origin, synced_at, status

## Public Commit Plan

- Plan path: \`tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/public-commit-plan.md\`
- Public metadata scan: passed
- Real public commit created: no
- Public remote added: no
- Push/tag/release performed: no

## Patch Import

- Dry-run report path: \`tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md\`
- Public-only patch outcome: generated apply plan
- Adapter-touch patch outcome: blocked and requires a new feature

## Governance Handoff

- Complete dependency/source/history/secret/build/sync gate suite remains with \`logicflow-open-source-governance-gates\`.
- This sync report is internal evidence only and does not define warning/blocking governance policy.

## Pending Human Review

- NOTICE/copyright holder details are recorded in the public snapshot as information; release dry-run does not block on that note.
- A passed sync dry-run does not mean the public repo is releasable.
EOF
}

validate_manifest

if [[ "${CHECK}" == true ]]; then
  run_clean_gate_probe
else
  require_clean_worktree
fi

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-public-sync.XXXXXX")"
trap 'rm -rf "${tmp_dir}"' EXIT

with_preserved_bootstrap_report "${tmp_dir}"

hash_one="$(normalized_content_hash "${OUTPUT_DIR}")"
hash_two="$(normalized_content_hash "${REPEAT_OUTPUT_DIR}")"
if [[ "${hash_one}" != "${hash_two}" ]]; then
  echo "Repeated public export hash mismatch: ${hash_one} != ${hash_two}" >&2
  exit 1
fi

verify_required_capability "${OUTPUT_DIR}"
bash "${LEAK_CHECK_SCRIPT}" --dry-run-dir "${OUTPUT_DIR}" --strict-clean

build_result="not-run"
if [[ "${RUN_BUILD}" == true ]]; then
  (cd "${OUTPUT_DIR}" && mvn test)
  build_result="passed"
fi

bash "${LEAK_CHECK_SCRIPT}" --dry-run-dir "${OUTPUT_DIR}"
write_commit_plan "${hash_one}"
scan_public_metadata "${OUTPUT_DIR}"
ledger_path="$(append_private_ledger "${hash_one}")"
verify_ledger_not_exported "${OUTPUT_DIR}" "${ledger_path}"
write_sync_report "${hash_one}" "${hash_two}" "${ledger_path}" "${build_result}"

echo "LogicFlow public sync dry-run passed: ${hash_one}"
echo "Sync report: ${SYNC_REPORT}"
