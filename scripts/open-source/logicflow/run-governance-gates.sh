#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
ROADMAP_DIR="${REPO_ROOT}/scripts/open-source/manifests/logicflow"
MANIFEST="${ROADMAP_DIR}/logicflow-open-source-governance-gates.yaml"
BOOTSTRAP_MANIFEST="${ROADMAP_DIR}/logicflow-public-repo-bootstrap.yaml"
BOUNDARY_MANIFEST="${ROADMAP_DIR}/logicflow-open-source-boundary.yaml"
ADAPTER_MANIFEST="${ROADMAP_DIR}/logicflow-platform-adapter-boundary.yaml"
CLASSIFICATION_MANIFEST="${ROADMAP_DIR}/logicflow-extension-bundle-classification.yaml"
SYNC_MANIFEST="${ROADMAP_DIR}/logicflow-public-sync-pipeline.yaml"
BOOTSTRAP_SCRIPT="${SCRIPT_DIR}/bootstrap-public-repo-dry-run.sh"
LEAK_CHECK_SCRIPT="${SCRIPT_DIR}/check-public-repo-leaks.sh"
SYNC_SCRIPT="${SCRIPT_DIR}/sync-public-repo-dry-run.sh"
PATCH_IMPORT_SCRIPT="${SCRIPT_DIR}/import-public-patch-dry-run.sh"
BOOTSTRAP_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-repo-bootstrap/bootstrap-report.md"
SYNC_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/sync-report.md"
METADATA_SCAN_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/metadata-scan-report.md"
PATCH_IMPORT_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md"
COMMIT_PLAN="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/public-commit-plan.md"
ARTIFACT_DIR="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-open-source-governance-gates"
JSON_REPORT="${ARTIFACT_DIR}/governance-gate-report.json"
MARKDOWN_REPORT="${ARTIFACT_DIR}/governance-gate-report.md"
NEGATIVE_REPORT="${ARTIFACT_DIR}/negative-probe-report.md"
DRY_RUN_DIR="${REPO_ROOT}/target/logicflow-public-repo-dry-run"
REPO_DIR=""
CHECK=false

usage() {
  cat <<'USAGE'
Usage: run-governance-gates.sh [--check] [--dry-run-dir DIR] [--repo-dir DIR]

Runs the internal LogicFlow open-source governance gates over the generated
public snapshot. When --repo-dir is provided, the same history/secret gates also
inspect local throwaway public repo metadata. The runner does not create a real
public repository, remote, push, tag, or release.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK=true
      shift
      ;;
    --dry-run-dir)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --dry-run-dir" >&2
        exit 2
      fi
      DRY_RUN_DIR="$2"
      shift 2
      ;;
    --repo-dir)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --repo-dir" >&2
        exit 2
      fi
      REPO_DIR="$2"
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

if [[ "${DRY_RUN_DIR}" != /* ]]; then
  DRY_RUN_DIR="${REPO_ROOT}/${DRY_RUN_DIR}"
fi
if [[ -n "${REPO_DIR}" && "${REPO_DIR}" != /* ]]; then
  REPO_DIR="${REPO_ROOT}/${REPO_DIR}"
fi

mkdir -p "${ARTIFACT_DIR}"
GATES_FILE="${ARTIFACT_DIR}/gate-results.tsv"
BLOCKING_FILE="${ARTIFACT_DIR}/blocking-items.tsv"
: > "${GATES_FILE}"
: > "${BLOCKING_FILE}"
: > "${NEGATIVE_REPORT}"

TECHNICAL_FAILED=false
BOOTSTRAP_OUTPUT=""

compact() {
  printf '%s' "$1" | tr '\n' ' ' | sed -E 's/[[:space:]]+/ /g' | cut -c 1-1600
}

record_gate() {
  local gate_id="$1"
  local status="$2"
  local evidence="$3"
  printf '%s\t%s\t%s\n' "${gate_id}" "${status}" "$(compact "${evidence}")" >> "${GATES_FILE}"
}

record_blocking_item() {
  local source="$1"
  local item="$2"
  local blocks="$3"
  printf '%s\t%s\t%s\n' "${source}" "$(compact "${item}")" "${blocks}" >> "${BLOCKING_FILE}"
}

append_negative_evidence() {
  local category="$1"
  local result="$2"
  printf -- '- %s: %s\n' "${category}" "${result}" >> "${NEGATIVE_REPORT}"
}

parse_yaml_list() {
  local file="$1"
  local section="$2"
  awk -v section="${section}" '
    $0 ~ "^" section ":" { in_section = 1; next }
    in_section && /^[A-Za-z0-9_]+:/ { exit }
    in_section && /^[[:space:]]+- / {
      sub(/^[[:space:]]+- /, "")
      gsub(/^"|"$/, "")
      print
    }
  ' "${file}"
}

parse_public_module_sources() {
  awk '
    /^public_modules:/ { in_public = 1; next }
    in_public && /^[a-z_]+:/ { exit }
    in_public && /^[[:space:]]+source:/ { source = $2 }
    in_public && /^[[:space:]]+target:/ {
      target = $2
      if (source != "" && target != "") {
        print source "|" target
      }
      source = ""
      target = ""
    }
  ' "${BOOTSTRAP_MANIFEST}"
}

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "Missing required input: ${file}" >&2
    return 1
  fi
}

check_required_inputs() {
  local input
  for input in \
    "${MANIFEST}" \
    "${BOOTSTRAP_MANIFEST}" \
    "${BOUNDARY_MANIFEST}" \
    "${ADAPTER_MANIFEST}" \
    "${CLASSIFICATION_MANIFEST}" \
    "${SYNC_MANIFEST}" \
    "${BOOTSTRAP_SCRIPT}" \
    "${LEAK_CHECK_SCRIPT}" \
    "${SYNC_SCRIPT}" \
    "${PATCH_IMPORT_SCRIPT}"; do
    require_file "${input}"
  done

  if rg -n "^(public_modules|public_paths|include_paths|exclude_paths|public_include|public_exclude|path_include|path_exclude):" "${MANIFEST}" >/dev/null; then
    echo "Gate manifest must not define a second public module/path include/exclude list" >&2
    return 1
  fi
}

prepare_public_snapshot() {
  local saved_report=""
  local bootstrap_status=0
  if [[ -f "${BOOTSTRAP_REPORT}" ]]; then
    saved_report="$(mktemp "${TMPDIR:-/tmp}/logicflow-bootstrap-report.XXXXXX")"
    cp "${BOOTSTRAP_REPORT}" "${saved_report}"
  fi

  if ! BOOTSTRAP_OUTPUT="$(bash "${BOOTSTRAP_SCRIPT}" --check --output "${DRY_RUN_DIR}" 2>&1)"; then
    bootstrap_status=1
  fi

  if [[ -n "${saved_report}" ]]; then
    mkdir -p "$(dirname "${BOOTSTRAP_REPORT}")"
    cp "${saved_report}" "${BOOTSTRAP_REPORT}"
    rm -f "${saved_report}"
  elif [[ -f "${BOOTSTRAP_REPORT}" ]]; then
    rm -f "${BOOTSTRAP_REPORT}"
  fi

  return "${bootstrap_status}"
}

scan_dependency_target() {
  local target_dir="$1"
  local label="$2"
  local failures=0
  local artifact prefix prefix_regex

  if [[ ! -d "${target_dir}" ]]; then
    echo "${label}: missing scan target ${target_dir}" >&2
    return 1
  fi

  while IFS= read -r artifact; do
    [[ -z "${artifact}" ]] && continue
    if grep -R -n --exclude-dir=target "<artifactId>${artifact}</artifactId>" "${target_dir}" --include='pom.xml' >/dev/null; then
      echo "${label}: forbidden Maven artifact ${artifact}" >&2
      failures=$((failures + 1))
    fi
  done < <(parse_yaml_list "${BOOTSTRAP_MANIFEST}" "forbidden_maven_artifacts")

  while IFS= read -r prefix; do
    [[ -z "${prefix}" ]] && continue
    prefix_regex="${prefix//./\\.}"
    if grep -R -n -E --exclude-dir=target "${prefix_regex}" "${target_dir}" \
        --include='*.java' --include='*.xml' --include='*.json' >/dev/null; then
      echo "${label}: forbidden import/prefix ${prefix}" >&2
      failures=$((failures + 1))
    fi
  done < <(parse_yaml_list "${BOOTSTRAP_MANIFEST}" "forbidden_import_prefixes")

  if [[ "$(basename "${target_dir}")" != "ouroboros-logicflow-data-model" ]]; then
    if grep -R -n -E --exclude-dir=target 'com\.ouroboros\.data' "${target_dir}" \
        --include='*.java' --include='*.xml' --include='*.json' >/dev/null; then
      echo "${label}: com.ouroboros.data is only allowed in ouroboros-logicflow-data-model" >&2
      failures=$((failures + 1))
    fi
    if grep -R -n --exclude-dir=target '<artifactId>ouroboros-data-core</artifactId>' "${target_dir}" --include='pom.xml' >/dev/null; then
      echo "${label}: ouroboros-data-core is only allowed in ouroboros-logicflow-data-model" >&2
      failures=$((failures + 1))
    fi
  fi

  [[ ${failures} -eq 0 ]]
}

run_dependency_negative_probe() {
  local tmp_dir="$1"
  local bad_dir="${tmp_dir}/forbidden-dependency"
  mkdir -p "${bad_dir}/src/main/java/com/example"
  cat > "${bad_dir}/pom.xml" <<'EOF'
<project><artifactId>probe</artifactId><dependencies><dependency><artifactId>ouroboros-data-core</artifactId></dependency></dependencies></project>
EOF
  cat > "${bad_dir}/src/main/java/com/example/ForbiddenImport.java" <<'EOF'
package com.example;
import com.ouroboros.data.Forbidden;
class ForbiddenImport {}
EOF

  if scan_dependency_target "${bad_dir}" "negative-dependency-probe" >/dev/null 2>&1; then
    echo "forbidden dependency/import probe was not blocked" >&2
    return 1
  fi
  append_negative_evidence "forbidden_dependency" "blocked forbidden artifact/import sample"
}

check_dependency_gate() {
  local entry source target
  local failures=0
  while IFS='|' read -r source target; do
    [[ -z "${source}" || -z "${target}" ]] && continue
    scan_dependency_target "${REPO_ROOT}/${source}" "source:${source}" || failures=$((failures + 1))
    scan_dependency_target "${DRY_RUN_DIR}/${target}" "snapshot:${target}" || failures=$((failures + 1))
  done < <(parse_public_module_sources)

  local tmp_dir
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-gate-dependency.XXXXXX")"
  if ! run_dependency_negative_probe "${tmp_dir}"; then
    rm -rf "${tmp_dir}"
    return 1
  fi
  rm -rf "${tmp_dir}"

  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi

  echo "Dependency gate passed for public candidate sources and generated snapshot."
}

require_snapshot_path() {
  local path="$1"
  if [[ ! -e "${DRY_RUN_DIR}/${path}" ]]; then
    echo "Missing required public snapshot path: ${path}" >&2
    return 1
  fi
}

scan_source_forbidden_paths() {
  local snapshot_dir="$1"
  local strict_clean="$2"
  local failures=0
  local forbidden

  if find "${snapshot_dir}" -type d -name .git -print -quit | grep -q .; then
    echo "snapshot contains .git ancestry" >&2
    failures=$((failures + 1))
  fi
  if find "${snapshot_dir}" -name .DS_Store -print -quit | grep -q .; then
    echo "snapshot contains .DS_Store" >&2
    failures=$((failures + 1))
  fi
  if [[ "${strict_clean}" == true ]] && (cd "${snapshot_dir}" && find . -path '*/target/*' -print -quit) | grep -q .; then
    echo "snapshot contains target output before build gate" >&2
    failures=$((failures + 1))
  fi

  while IFS= read -r forbidden; do
    [[ -z "${forbidden}" ]] && continue
    case "${forbidden}" in
      .git)
        ;;
      target)
        ;;
      *)
        if [[ -e "${snapshot_dir}/${forbidden}" ]]; then
          echo "snapshot contains forbidden path: ${forbidden}" >&2
          failures=$((failures + 1))
        fi
        ;;
    esac
  done < <(parse_yaml_list "${BOOTSTRAP_MANIFEST}" "forbidden_paths")

  [[ ${failures} -eq 0 ]]
}

verify_required_capability() {
  local sample_test="${DRY_RUN_DIR}/samples/script-node-standalone/src/test/java/com/ouroboros/logicflow/samples/ScriptNodeStandaloneSampleTest.java"
  local script_define="${DRY_RUN_DIR}/ouroboros-logicflow-authoring/src/main/metadata/ScriptNode.flow-node.json"
  local node_builder_descriptor="${DRY_RUN_DIR}/ouroboros-logicflow-builtin-nodes/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder"

  [[ -f "${sample_test}" ]] || { echo "Missing ScriptNode standalone sample test" >&2; return 1; }
  [[ -f "${script_define}" ]] || { echo "Missing ScriptNode NodeDefine JSON" >&2; return 1; }
  [[ -f "${node_builder_descriptor}" ]] || { echo "Missing NodeBuilder SPI descriptor" >&2; return 1; }
  grep -q "ScriptNode" "${node_builder_descriptor}" || { echo "NodeBuilder SPI descriptor does not include ScriptNode" >&2; return 1; }
}

run_source_negative_probe() {
  local tmp_dir="$1"
  local bad_dir="${tmp_dir}/forbidden-source"
  mkdir -p "${bad_dir}/.codestable"
  if scan_source_forbidden_paths "${bad_dir}" false >/dev/null 2>&1; then
    echo "forbidden source path probe was not blocked" >&2
    return 1
  fi
  append_negative_evidence "forbidden_source_path" "blocked .codestable path sample"
}

check_source_gate() {
  local failures=0
  bash "${LEAK_CHECK_SCRIPT}" --dry-run-dir "${DRY_RUN_DIR}" --strict-clean || failures=$((failures + 1))
  for required in \
    pom.xml \
    README.md \
    LICENSE \
    NOTICE \
    OPEN_SOURCE_SNAPSHOT.md \
    .github/workflows/build.yml \
    ouroboros-logicflow-bom/pom.xml \
    ouroboros-logicflow-core/pom.xml \
    ouroboros-logicflow-builtin-nodes/pom.xml \
    ouroboros-logicflow-authoring/pom.xml \
    samples/script-node-standalone/pom.xml; do
    require_snapshot_path "${required}" || failures=$((failures + 1))
  done
  scan_source_forbidden_paths "${DRY_RUN_DIR}" true || failures=$((failures + 1))
  verify_required_capability || failures=$((failures + 1))

  local tmp_dir
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-gate-source.XXXXXX")"
  if ! run_source_negative_probe "${tmp_dir}"; then
    rm -rf "${tmp_dir}"
    return 1
  fi
  rm -rf "${tmp_dir}"

  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi

  echo "Source gate passed for required public surface, forbidden paths, and ScriptNode evidence."
}

scan_history_markers() {
  local scan_root="$1"
  local label="$2"
  local failures=0

  if [[ -d "${scan_root}" ]] && find "${scan_root}" -type d -name .git -print -quit | grep -q .; then
    echo "${label}: contains .git ancestry" >&2
    failures=$((failures + 1))
  fi

  if grep -R -n -E --exclude-dir=target '\b[0-9a-f]{40}\b' "${scan_root}" >/dev/null 2>&1; then
    echo "${label}: contains 40-character hex marker" >&2
    failures=$((failures + 1))
  fi
  if grep -R -n -E --exclude-dir=target '(/Users/|/Projects/ouroboros|\.worktrees|\.codestable|scripts/open-source|core/ouroboros-logicflow)' "${scan_root}" >/dev/null 2>&1; then
    echo "${label}: contains internal path or workspace marker" >&2
    failures=$((failures + 1))
  fi
  if grep -R -n -E --exclude-dir=target '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "${scan_root}" >/dev/null 2>&1; then
    echo "${label}: contains author/email marker" >&2
    failures=$((failures + 1))
  fi
  if grep -R -n -E --exclude-dir=target '(codex/|\.codestable/features|logicflow-public-sync-pipeline|logicflow-open-source-governance-gates)' "${scan_root}" >/dev/null 2>&1; then
    echo "${label}: contains internal branch/task marker" >&2
    failures=$((failures + 1))
  fi

  [[ ${failures} -eq 0 ]]
}

scan_secret_markers() {
  local scan_root="$1"
  local label="$2"
  local failures=0
  local secret_pattern='(AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|internal_base_ref|private-sync-ledger|governance-gate-report)'

  if grep -R -n -E --exclude-dir=target "${secret_pattern}" "${scan_root}" >/dev/null 2>&1; then
    echo "${label}: contains sensitive or private marker" >&2
    failures=$((failures + 1))
  fi

  [[ ${failures} -eq 0 ]]
}

run_history_secret_negative_probe() {
  local tmp_dir="$1"
  local bad_dir="${tmp_dir}/forbidden-metadata"
  mkdir -p "${bad_dir}"
  cat > "${bad_dir}/metadata.txt" <<'EOF'
Internal path /Users/songmingxu/Projects/ouroboros/.worktrees/example
Secret sample AKIA1234567890ABCDEF
EOF
  if scan_history_markers "${bad_dir}" "negative-history-probe" >/dev/null 2>&1; then
    echo "internal metadata probe was not blocked" >&2
    return 1
  fi
  if scan_secret_markers "${bad_dir}" "negative-secret-probe" >/dev/null 2>&1; then
    echo "sensitive marker probe was not blocked" >&2
    return 1
  fi
  append_negative_evidence "internal_metadata_marker" "blocked internal path marker sample"
  append_negative_evidence "sensitive_marker" "blocked credential-like marker sample"
}

scan_repo_worktree_markers() {
  local repo_dir="$1"
  local label="$2"
  local failures=0
  local pattern='(\b[0-9a-f]{40}\b|/Users/|/Projects/ouroboros|\.worktrees|\.codestable|scripts/open-source|core/ouroboros-logicflow|codex/|\.codestable/features|logicflow-public-sync-pipeline|logicflow-open-source-governance-gates)'

  if git -C "${repo_dir}" grep -n -E "${pattern}" -- . >/dev/null 2>&1; then
    echo "${label}: tracked files contain internal history/path/task marker" >&2
    failures=$((failures + 1))
  fi

  [[ ${failures} -eq 0 ]]
}

scan_repo_commit_metadata() {
  local repo_dir="$1"
  local label="$2"
  local failures=0
  local metadata_file
  metadata_file="$(mktemp "${TMPDIR:-/tmp}/logicflow-repo-metadata.XXXXXX")"

  git -C "${repo_dir}" log --format='%an%n%ae%n%s%n%b' > "${metadata_file}"

  if grep -n -E '\b[0-9a-f]{40}\b' "${metadata_file}" >/dev/null 2>&1; then
    echo "${label}: commit metadata contains 40-character internal ref marker" >&2
    failures=$((failures + 1))
  fi
  if grep -n -E '(/Users/|/Projects/ouroboros|\.worktrees|\.codestable|scripts/open-source|core/ouroboros-logicflow)' "${metadata_file}" >/dev/null 2>&1; then
    echo "${label}: commit metadata contains internal path or workspace marker" >&2
    failures=$((failures + 1))
  fi
  if grep -n -E '(codex/|\.codestable/features|logicflow-public-sync-pipeline|logicflow-open-source-governance-gates)' "${metadata_file}" >/dev/null 2>&1; then
    echo "${label}: commit metadata contains internal branch/task marker" >&2
    failures=$((failures + 1))
  fi
  if grep -n -E '(songmingxu|@ouroboros\.internal|@.*\.corp|@.*\.local)' "${metadata_file}" >/dev/null 2>&1; then
    echo "${label}: commit metadata contains internal author email marker" >&2
    failures=$((failures + 1))
  fi

  rm -f "${metadata_file}"
  [[ ${failures} -eq 0 ]]
}

check_local_public_repo() {
  local repo_dir="$1"
  local label="local-public-repo"
  local failures=0

  if [[ -z "${repo_dir}" ]]; then
    echo "Missing repo mode input" >&2
    return 1
  fi
  if [[ "${repo_dir}" != "${REPO_ROOT}/target/"* ]]; then
    echo "${label}: repo mode input must be under target/: ${repo_dir}" >&2
    failures=$((failures + 1))
  fi
  if [[ ! -d "${repo_dir}/.git" ]]; then
    echo "${label}: missing .git directory" >&2
    failures=$((failures + 1))
  fi
  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi
  if [[ -n "$(git -C "${repo_dir}" remote)" ]]; then
    echo "${label}: public remote is configured" >&2
    failures=$((failures + 1))
  fi
  if [[ -n "$(git -C "${repo_dir}" tag -l)" ]]; then
    echo "${label}: tag exists" >&2
    failures=$((failures + 1))
  fi
  if [[ "$(git -C "${repo_dir}" rev-list --count HEAD 2>/dev/null || printf '0')" == "0" ]]; then
    echo "${label}: missing dry-run commit" >&2
    failures=$((failures + 1))
  fi

  scan_repo_worktree_markers "${repo_dir}" "${label}" || failures=$((failures + 1))
  scan_repo_commit_metadata "${repo_dir}" "${label}" || failures=$((failures + 1))

  [[ ${failures} -eq 0 ]]
}

scan_repo_secret_markers() {
  local repo_dir="$1"
  local label="$2"
  local failures=0
  local secret_pattern='(AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|internal_base_ref|private-sync-ledger|governance-gate-report)'

  if git -C "${repo_dir}" grep -n -E "${secret_pattern}" -- . >/dev/null 2>&1; then
    echo "${label}: tracked files contain sensitive or private marker" >&2
    failures=$((failures + 1))
  fi

  [[ ${failures} -eq 0 ]]
}

check_history_gate() {
  local failures=0
  scan_history_markers "${DRY_RUN_DIR}" "public-snapshot" || failures=$((failures + 1))
  if [[ -f "${COMMIT_PLAN}" ]]; then
    scan_history_markers "${COMMIT_PLAN}" "public-commit-plan" || failures=$((failures + 1))
  fi
  if [[ -n "${REPO_DIR}" ]]; then
    check_local_public_repo "${REPO_DIR}" || failures=$((failures + 1))
  fi

  local tmp_dir
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/logicflow-gate-history.XXXXXX")"
  if ! run_history_secret_negative_probe "${tmp_dir}"; then
    rm -rf "${tmp_dir}"
    return 1
  fi
  rm -rf "${tmp_dir}"

  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi

  if [[ -n "${REPO_DIR}" ]]; then
    echo "History gate passed in snapshot mode and local public repo mode."
  else
    echo "History gate passed in snapshot mode. Repo mode is deferred to release dry-run input."
  fi
}

check_secret_gate() {
  local failures=0
  scan_secret_markers "${DRY_RUN_DIR}" "public-snapshot" || failures=$((failures + 1))
  if [[ -f "${COMMIT_PLAN}" ]]; then
    scan_secret_markers "${COMMIT_PLAN}" "public-commit-plan" || failures=$((failures + 1))
  fi
  if [[ -n "${REPO_DIR}" ]]; then
    scan_repo_secret_markers "${REPO_DIR}" "local-public-repo" || failures=$((failures + 1))
    if [[ ${failures} -gt 0 ]]; then
      return 1
    fi
    echo "Secret gate passed for snapshot, public commit plan, and local public repo markers."
  else
    if [[ ${failures} -gt 0 ]]; then
      return 1
    fi
    echo "Secret gate passed for snapshot and public commit plan markers."
  fi
}

check_build_gate() {
  local failures=0
  (cd "${DRY_RUN_DIR}" && mvn test) || failures=$((failures + 1))
  bash "${LEAK_CHECK_SCRIPT}" --dry-run-dir "${DRY_RUN_DIR}" || failures=$((failures + 1))
  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi
  echo "Build gate passed for generated public snapshot."
}

check_sync_gate() {
  local failures=0
  bash "${SYNC_SCRIPT}" --check --output "${DRY_RUN_DIR}" || failures=$((failures + 1))
  bash "${PATCH_IMPORT_SCRIPT}" --check || failures=$((failures + 1))
  grep -q "Hashes match: yes" "${SYNC_REPORT}" || failures=$((failures + 1))
  grep -q "Public metadata scan: passed" "${SYNC_REPORT}" || failures=$((failures + 1))
  grep -q "Adapter-touch fixture patch was blocked" "${PATCH_IMPORT_REPORT}" || failures=$((failures + 1))
  grep -q "Public-only fixture patch produced an apply plan" "${PATCH_IMPORT_REPORT}" || failures=$((failures + 1))
  if [[ ${failures} -gt 0 ]]; then
    return 1
  fi
  echo "Sync gate passed: repeated export hash, metadata scan, private ledger check, and patch import checks all passed."
}

run_gate() {
  local gate_id="$1"
  local gate_label="$2"
  local output
  shift 2

  if output="$("$@" 2>&1)"; then
    record_gate "${gate_id}" "passed" "${gate_label}: ${output}"
  else
    TECHNICAL_FAILED=true
    record_gate "${gate_id}" "failed" "${gate_label}: ${output}"
    record_blocking_item "gate:${gate_id}" "${gate_label} failed" "technical_gates_status"
  fi
}

add_pending_owner_reviews() {
  if grep -R -n "NOTICE/copyright holder review" "${MANIFEST}" "${BOOTSTRAP_REPORT}" "${SYNC_REPORT}" >/dev/null 2>&1; then
    record_blocking_item "pending_owner_review" "NOTICE/copyright holder details recorded as information" "release_governance_status"
  fi
}

write_reports() {
  local generated_at
  local report_mode="snapshot"
  local repo_mode="deferred_to_logicflow_open_source_release_dry_run"
  local repo_dir_rel=""
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  if [[ -n "${REPO_DIR}" ]]; then
    report_mode="snapshot+repo"
    repo_mode="local_public_repo_dry_run"
    repo_dir_rel="${REPO_DIR#${REPO_ROOT}/}"
  fi
  python3 - "${GATES_FILE}" "${BLOCKING_FILE}" "${JSON_REPORT}" "${MARKDOWN_REPORT}" "${generated_at}" "${NEGATIVE_REPORT}" "${report_mode}" "${repo_mode}" "${repo_dir_rel}" <<'PY'
import json
import sys
from pathlib import Path

gates_file = Path(sys.argv[1])
blocking_file = Path(sys.argv[2])
json_report = Path(sys.argv[3])
markdown_report = Path(sys.argv[4])
generated_at = sys.argv[5]
negative_report = Path(sys.argv[6])
report_mode = sys.argv[7]
repo_mode = sys.argv[8]
repo_dir_rel = sys.argv[9]

order = ["dependency", "source", "history", "secret", "build", "sync"]
raw_gates = {}
for line in gates_file.read_text().splitlines():
    if not line.strip():
        continue
    gate_id, status, evidence = (line.split("\t") + ["", "", ""])[:3]
    raw_gates[gate_id] = {
        "id": gate_id,
        "status": status,
        "blocking": True,
        "evidence": [evidence] if evidence else [],
    }

gates = []
for gate_id in order:
    gates.append(raw_gates.get(gate_id, {
        "id": gate_id,
        "status": "failed",
        "blocking": True,
        "evidence": ["gate did not run"],
    }))

blocking_items = []
seen = set()
for line in blocking_file.read_text().splitlines():
    if not line.strip():
        continue
    source, item, blocks = (line.split("\t") + ["", "", ""])[:3]
    key = (source, item, blocks)
    if key in seen:
        continue
    seen.add(key)
    blocking_items.append({
        "source": source,
        "item": item,
        "blocks": blocks,
    })

technical_failed = any(gate["status"] != "passed" for gate in gates)
technical_status = "failed" if technical_failed else "passed"
release_blocked = technical_failed or any(item["blocks"] == "release_governance_status" for item in blocking_items)
release_status = "blocked" if release_blocked else "passed"

report = {
    "gate_suite": "logicflow-open-source-governance-gates",
    "mode": report_mode,
    "repo_mode": repo_mode,
    "generated_at": generated_at,
    "technical_gates_status": technical_status,
    "release_governance_status": release_status,
    "gates": gates,
    "blocking_items": blocking_items,
    "reports": {
        "json": "tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.json",
        "markdown": "tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.md",
        "negative_probe": "tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/negative-probe-report.md",
    },
    "publication_performed": {
        "public_repo_created": False,
        "public_remote_added": False,
        "push": False,
        "tag": False,
        "release": False,
    },
    "local_public_repo_dry_run": {
        "provided": repo_mode == "local_public_repo_dry_run",
        "path": repo_dir_rel,
        "real_public_repo": False,
    },
}

json_report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n")

lines = [
    "---",
    "doc_type: logicflow-open-source-governance-gate-report",
    "roadmap: logicflow-open-source-extraction",
    "feature: 2026-07-26-logicflow-open-source-governance-gates",
    f"technical_gates_status: {technical_status}",
    f"release_governance_status: {release_status}",
    f"updated: {generated_at}",
    "---",
    "",
    "# LogicFlow Open Source Governance Gate Report",
    "",
    "## Status",
    "",
    f"- Technical gates status: `{technical_status}`",
    f"- Release governance status: `{release_status}`",
    f"- Mode: `{report_mode}`",
    f"- Repo mode: `{repo_mode}`",
    "",
    "## Gates",
    "",
]
for gate in gates:
    lines.append(f"- `{gate['id']}`: `{gate['status']}`")
lines.extend(["", "## Blocking Items", ""])
if blocking_items:
    for item in blocking_items:
        lines.append(f"- `{item['blocks']}`: {item['item']} ({item['source']})")
else:
    lines.append("- none")
lines.extend([
    "",
    "## Negative Probes",
    "",
])
if negative_report.exists() and negative_report.read_text().strip():
    lines.extend(negative_report.read_text().splitlines())
else:
    lines.append("- none")
lines.extend([
    "",
    "## Publication Actions",
    "",
    "- Public repo created: no",
    "- Public remote added: no",
    "- Push/tag/release: no",
    f"- Local public repo dry-run: `{repo_dir_rel or 'not provided'}`",
    "",
    "## Handoff",
    "",
    "- `logicflow-open-source-release-dry-run` can consume the JSON report.",
    "- This report does not create a release checklist or residual risk report.",
])
markdown_report.write_text("\n".join(lines) + "\n")
PY
}

if ! check_required_inputs; then
  TECHNICAL_FAILED=true
  record_gate "dependency" "failed" "Required manifest/script input missing or gate manifest copied public boundary."
  record_blocking_item "gate:dependency" "required gate inputs missing or invalid" "technical_gates_status"
  write_reports
  exit 1
fi

if ! prepare_public_snapshot; then
  TECHNICAL_FAILED=true
  record_gate "source" "failed" "Bootstrap public snapshot failed: ${BOOTSTRAP_OUTPUT}"
  record_gate "dependency" "failed" "Skipped because public snapshot bootstrap failed."
  record_gate "history" "failed" "Skipped because public snapshot bootstrap failed."
  record_gate "secret" "failed" "Skipped because public snapshot bootstrap failed."
  record_gate "build" "failed" "Skipped because public snapshot bootstrap failed."
  record_gate "sync" "failed" "Skipped because public snapshot bootstrap failed."
  record_blocking_item "bootstrap" "public snapshot bootstrap failed" "technical_gates_status"
  add_pending_owner_reviews
  write_reports
  exit 1
fi

run_gate "dependency" "Dependency gate" check_dependency_gate
run_gate "source" "Source gate" check_source_gate
run_gate "build" "Build gate" check_build_gate
run_gate "sync" "Sync gate" check_sync_gate
run_gate "history" "History gate" check_history_gate
run_gate "secret" "Secret gate" check_secret_gate
add_pending_owner_reviews
write_reports

if [[ "${TECHNICAL_FAILED}" == true ]]; then
  echo "LogicFlow governance gates failed. Report: ${JSON_REPORT}" >&2
  exit 1
fi

if [[ "${CHECK}" == true ]]; then
  echo "LogicFlow governance technical gates passed; release governance remains informational for NOTICE/copyright holder details only."
  echo "Governance report: ${JSON_REPORT}"
else
  echo "LogicFlow governance gates completed. Report: ${JSON_REPORT}"
fi
