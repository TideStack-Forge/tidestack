#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
ROADMAP_DIR="${REPO_ROOT}/scripts/open-source/manifests/logicflow"
MANIFEST="${ROADMAP_DIR}/logicflow-open-source-release-dry-run.yaml"
BOOTSTRAP_MANIFEST="${ROADMAP_DIR}/logicflow-public-repo-bootstrap.yaml"
BOUNDARY_MANIFEST="${ROADMAP_DIR}/logicflow-open-source-boundary.yaml"
ADAPTER_MANIFEST="${ROADMAP_DIR}/logicflow-platform-adapter-boundary.yaml"
CLASSIFICATION_MANIFEST="${ROADMAP_DIR}/logicflow-extension-bundle-classification.yaml"
SYNC_MANIFEST="${ROADMAP_DIR}/logicflow-public-sync-pipeline.yaml"
GOVERNANCE_MANIFEST="${ROADMAP_DIR}/logicflow-open-source-governance-gates.yaml"
BOOTSTRAP_SCRIPT="${SCRIPT_DIR}/bootstrap-public-repo-dry-run.sh"
LEAK_CHECK_SCRIPT="${SCRIPT_DIR}/check-public-repo-leaks.sh"
SYNC_SCRIPT="${SCRIPT_DIR}/sync-public-repo-dry-run.sh"
PATCH_IMPORT_SCRIPT="${SCRIPT_DIR}/import-public-patch-dry-run.sh"
GOVERNANCE_SCRIPT="${SCRIPT_DIR}/run-governance-gates.sh"
TARGET_ROOT="${REPO_ROOT}/target/logicflow-open-source-release-dry-run"
SNAPSHOT_DIR="${TARGET_ROOT}/snapshot"
PUBLIC_REPO_DIR="${TARGET_ROOT}/public-repo"
ARTIFACT_DIR="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run"
JSON_REPORT="${ARTIFACT_DIR}/release-dry-run-report.json"
MARKDOWN_REPORT="${ARTIFACT_DIR}/release-dry-run-report.md"
RELEASE_CHECKLIST="${ARTIFACT_DIR}/release-checklist.md"
RESIDUAL_RISK_REPORT="${ARTIFACT_DIR}/residual-risk-report.md"
CLEAN_SOURCE_EVIDENCE="${ARTIFACT_DIR}/clean-source-evidence.md"
GOVERNANCE_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.json"
SYNC_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/sync-report.md"
PATCH_IMPORT_REPORT="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md"
CHECK=false
SOURCE_MODE="operational"
CURRENT_WORKTREE_CLEAN=false
CLEAN_CHECKOUT_STATUS="not-run"
DIRTY_SOURCE_PROBE_STATUS="not-run"
PREREQUISITE_INPUTS_STATUS="not-run"
PUBLIC_REPO_COMMIT_COUNT=0
CURRENT_STEP="startup"
REPORT_FINALIZED=false

usage() {
  cat <<'USAGE'
Usage: run-release-dry-run.sh [--check]

Runs the internal LogicFlow release dry-run. The runner creates only a local
throwaway public repo under target/, runs governance gates with repo mode input,
and writes internal release evidence. It never creates a real public repo,
remote, push, tag, or release.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK=true
      SOURCE_MODE="implementation-check"
      shift
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
    "${GOVERNANCE_MANIFEST}" \
    "${BOOTSTRAP_SCRIPT}" \
    "${LEAK_CHECK_SCRIPT}" \
    "${SYNC_SCRIPT}" \
    "${PATCH_IMPORT_SCRIPT}" \
    "${GOVERNANCE_SCRIPT}"; do
    require_file "${input}"
  done

  if rg -n "^(public_modules|public_paths|include_paths|exclude_paths|public_include|public_exclude|path_include|path_exclude):" "${MANIFEST}" >/dev/null; then
    echo "Release manifest must not define a second public module/path include/exclude list" >&2
    return 1
  fi

  PREREQUISITE_INPUTS_STATUS="passed"
}

require_clean_worktree() {
  if [[ "${LOGICFLOW_RELEASE_CLEAN_PROBE_DIRTY:-false}" == "true" ]]; then
    echo "Internal worktree is dirty; refusing operational release dry-run." >&2
    return 1
  fi
  if [[ -n "$(git -C "${REPO_ROOT}" status --porcelain --untracked-files=all)" ]]; then
    echo "Internal worktree is dirty; refusing operational release dry-run." >&2
    return 1
  fi
}

run_clean_source_gate() {
  if [[ -z "$(git -C "${REPO_ROOT}" status --porcelain --untracked-files=all)" ]]; then
    CURRENT_WORKTREE_CLEAN=true
  fi

  if [[ "${CHECK}" == true ]]; then
    if LOGICFLOW_RELEASE_CLEAN_PROBE_DIRTY=true require_clean_worktree >/dev/null 2>&1; then
      echo "Clean source negative probe failed: dirty source was accepted" >&2
      exit 1
    fi
    DIRTY_SOURCE_PROBE_STATUS="blocked"
    if [[ "${CURRENT_WORKTREE_CLEAN}" == true ]]; then
      CLEAN_CHECKOUT_STATUS="passed"
    else
      CLEAN_CHECKOUT_STATUS="not-asserted-in-check-mode"
    fi
  else
    require_clean_worktree
    CURRENT_WORKTREE_CLEAN=true
    DIRTY_SOURCE_PROBE_STATUS="not-run-operational-mode"
    CLEAN_CHECKOUT_STATUS="passed"
  fi

  cat > "${CLEAN_SOURCE_EVIDENCE}" <<EOF
# Clean Source Evidence

- Source mode: \`${SOURCE_MODE}\`
- Current worktree clean: \`${CURRENT_WORKTREE_CLEAN}\`
- Clean checkout status: \`${CLEAN_CHECKOUT_STATUS}\`
- Dirty source probe: \`${DIRTY_SOURCE_PROBE_STATUS}\`
- Required release inputs: \`${PREREQUISITE_INPUTS_STATUS}\`

Operational release dry-run mode refuses a dirty internal worktree. Check mode
is allowed to run from an implementation worktree so it can prove the dirty
source gate and generate internal evidence before the work is committed.
EOF
}

prepare_public_snapshot() {
  local bootstrap_report="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-repo-bootstrap/bootstrap-report.md"
  local saved_bootstrap_report=""
  if [[ -f "${bootstrap_report}" ]]; then
    saved_bootstrap_report="$(mktemp "${TMPDIR:-/tmp}/logicflow-bootstrap-report.XXXXXX")"
    cp "${bootstrap_report}" "${saved_bootstrap_report}"
  fi

  rm -rf "${TARGET_ROOT}"
  mkdir -p "${TARGET_ROOT}"
  bash "${BOOTSTRAP_SCRIPT}" --check --output "${SNAPSHOT_DIR}"

  if [[ -n "${saved_bootstrap_report}" ]]; then
    mkdir -p "$(dirname "${bootstrap_report}")"
    cp "${saved_bootstrap_report}" "${bootstrap_report}"
    rm -f "${saved_bootstrap_report}"
  elif [[ -f "${bootstrap_report}" ]]; then
    rm -f "${bootstrap_report}"
  fi
}

init_local_public_repo() {
  mkdir -p "${PUBLIC_REPO_DIR}"
  rsync -a --exclude='.git/' "${SNAPSHOT_DIR}/" "${PUBLIC_REPO_DIR}/"

  git -C "${PUBLIC_REPO_DIR}" init -q
  git -C "${PUBLIC_REPO_DIR}" config user.name "LogicFlow Maintainers"
  git -C "${PUBLIC_REPO_DIR}" config user.email "logicflow-maintainers@example.invalid"
  git -C "${PUBLIC_REPO_DIR}" add -A
  git -C "${PUBLIC_REPO_DIR}" commit -q -m "Initial LogicFlow public snapshot"
  PUBLIC_REPO_COMMIT_COUNT="$(git -C "${PUBLIC_REPO_DIR}" rev-list --count HEAD)"
}

verify_local_public_repo() {
  local metadata_file="${ARTIFACT_DIR}/local-public-repo-metadata.txt"

  if [[ "${PUBLIC_REPO_DIR}" != "${REPO_ROOT}/target/"* ]]; then
    echo "Local public repo dry-run must stay under target/: ${PUBLIC_REPO_DIR}" >&2
    exit 1
  fi
  if [[ -n "$(git -C "${PUBLIC_REPO_DIR}" remote)" ]]; then
    echo "Local public repo dry-run has a remote configured" >&2
    exit 1
  fi
  if [[ -n "$(git -C "${PUBLIC_REPO_DIR}" tag -l)" ]]; then
    echo "Local public repo dry-run has a tag" >&2
    exit 1
  fi
  if [[ "${PUBLIC_REPO_COMMIT_COUNT}" == "0" ]]; then
    echo "Local public repo dry-run has no commit" >&2
    exit 1
  fi

  git -C "${PUBLIC_REPO_DIR}" log --format='%an%n%ae%n%s' > "${metadata_file}"
  if grep -n -E '(\b[0-9a-f]{40}\b|/Users/|/Projects/ouroboros|\.worktrees|\.codestable|scripts/open-source|core/ouroboros-logicflow|codex/|\.codestable/features|logicflow-public-sync-pipeline|logicflow-open-source-governance-gates|songmingxu|@ouroboros\.internal|@.*\.corp|@.*\.local)' "${metadata_file}" >/dev/null 2>&1; then
    echo "Local public repo commit metadata contains internal marker" >&2
    exit 1
  fi
}

run_repo_mode_governance_gates() {
  bash "${GOVERNANCE_SCRIPT}" --check --dry-run-dir "${SNAPSHOT_DIR}" --repo-dir "${PUBLIC_REPO_DIR}"
  python3 -m json.tool "${GOVERNANCE_REPORT}" >/dev/null
}

run_public_repo_build_and_sync_checks() {
  (cd "${PUBLIC_REPO_DIR}" && mvn test)
  bash "${LEAK_CHECK_SCRIPT}" --dry-run-dir "${PUBLIC_REPO_DIR}" --allow-git-dir
  grep -q "Hashes match: yes" "${SYNC_REPORT}"
  grep -q "Public metadata scan: passed" "${SYNC_REPORT}"
  grep -q "Adapter-touch fixture patch was blocked" "${PATCH_IMPORT_REPORT}"
  grep -q "Public-only fixture patch produced an apply plan" "${PATCH_IMPORT_REPORT}"
}

write_release_reports() {
  local generated_at
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  python3 - "${JSON_REPORT}" "${MARKDOWN_REPORT}" "${RELEASE_CHECKLIST}" "${RESIDUAL_RISK_REPORT}" "${GOVERNANCE_REPORT}" "${generated_at}" "${SOURCE_MODE}" "${CURRENT_WORKTREE_CLEAN}" "${CLEAN_CHECKOUT_STATUS}" "${DIRTY_SOURCE_PROBE_STATUS}" "${PUBLIC_REPO_COMMIT_COUNT}" <<'PY'
import json
import sys
from pathlib import Path

json_report = Path(sys.argv[1])
markdown_report = Path(sys.argv[2])
release_checklist = Path(sys.argv[3])
residual_risk_report = Path(sys.argv[4])
governance_report = Path(sys.argv[5])
generated_at = sys.argv[6]
source_mode = sys.argv[7]
current_worktree_clean = sys.argv[8] == "true"
clean_checkout_status = sys.argv[9]
dirty_source_probe_status = sys.argv[10]
commit_count = int(sys.argv[11])

governance = json.loads(governance_report.read_text())
technical_status = "passed" if governance.get("technical_gates_status") == "passed" else "failed"
governance_release_status = governance.get("release_governance_status", "blocked")
clean_source_blocks_release = clean_checkout_status != "passed"
release_governance_status = "blocked" if clean_source_blocks_release or governance_release_status == "blocked" else "passed"

blocking_items = list(governance.get("blocking_items", []))
if clean_source_blocks_release:
    blocking_items.append({
        "source": "clean_source_gate",
        "item": "Operational clean source was not asserted in check mode",
        "blocks": "release_governance_status",
    })

report = {
    "release_dry_run": "logicflow-open-source-release-dry-run",
    "mode": "local-dry-run",
    "generated_at": generated_at,
    "source": {
        "mode": source_mode,
        "current_worktree_clean": current_worktree_clean,
        "clean_checkout": clean_checkout_status,
        "dirty_source_probe": dirty_source_probe_status,
        "source_ref_recorded_in_internal_report": False,
    },
    "public_repo_dry_run": {
        "path": "target/logicflow-open-source-release-dry-run/public-repo",
        "git_initialized": True,
        "commit_count": commit_count,
        "commit_metadata_scan": "passed",
        "public_remote_added": False,
        "push": False,
        "tag": False,
        "release": False,
    },
    "statuses": {
        "technical_dry_run_status": technical_status,
        "release_governance_status": release_governance_status,
        "publication_authorization_status": "not-authorized",
    },
    "consumed_reports": {
        "governance_gate_report": "tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.json",
        "sync_report": "tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/sync-report.md",
        "patch_import_report": "tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md",
    },
    "reports": {
        "release_checklist": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/release-checklist.md",
        "residual_risk": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/residual-risk-report.md",
        "markdown": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/release-dry-run-report.md",
    },
    "blocking_items": blocking_items,
    "publication_performed": {
        "public_repo_created": False,
        "public_remote_added": False,
        "push": False,
        "tag": False,
        "release": False,
    },
}
json_report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n")

status_lines = [
    "---",
    "doc_type: logicflow-open-source-release-dry-run-report",
    "roadmap: logicflow-open-source-extraction",
    "feature: 2026-07-26-logicflow-open-source-release-dry-run",
    f"technical_dry_run_status: {technical_status}",
    f"release_governance_status: {release_governance_status}",
    "publication_authorization_status: not-authorized",
    f"updated: {generated_at}",
    "---",
    "",
    "# LogicFlow Open Source Release Dry Run Report",
    "",
    "## Status",
    "",
    f"- Technical dry-run status: `{technical_status}`",
    f"- Release governance status: `{release_governance_status}`",
    "- Publication authorization status: `not-authorized`",
    f"- Source mode: `{source_mode}`",
    f"- Clean checkout status: `{clean_checkout_status}`",
    "- Local public repo dry-run: `target/logicflow-open-source-release-dry-run/public-repo`",
    "- Public remote / push / tag / release: no",
    "",
    "## Consumed Evidence",
    "",
    "- Governance gate report: `tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.json`",
    "- Sync report: `tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/sync-report.md`",
    "- Patch import report: `tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md`",
    "",
    "## Blocking Items",
    "",
]
if blocking_items:
    for item in blocking_items:
        status_lines.append(f"- `{item['blocks']}`: {item['item']} ({item['source']})")
else:
    status_lines.append("- none")
markdown_report.write_text("\n".join(status_lines) + "\n")

checklist_lines = [
    "# LogicFlow Release Checklist",
    "",
    "| Area | Status | Evidence |",
    "|---|---|---|",
    f"| Required inputs | passed | release manifest and referenced manifests/scripts exist |",
    f"| Clean source | {'passed' if clean_checkout_status == 'passed' else 'manual-review'} | clean checkout status `{clean_checkout_status}`, dirty source probe `{dirty_source_probe_status}` |",
    "| Public snapshot export | passed | bootstrap dry-run generated snapshot under target |",
    "| Local public repo dry-run | passed | git repo initialized under target with public-safe local commit |",
    f"| Governance technical gates | {technical_status} | consumed governance gate report with repo mode `{governance.get('repo_mode')}` |",
    "| Public build/test | passed | `mvn test` passed from local public repo dry-run |",
    "| Sync evidence | passed | sync hash, metadata scan, and patch import policy evidence consumed |",
    "| Publication authorization | blocked | real public repo/remote/push/tag/release are not authorized |",
    "| No publication actions | passed | report fields keep public repo creation, remote, push, tag, release false |",
]
release_checklist.write_text("\n".join(checklist_lines) + "\n")

risk_lines = [
    "# LogicFlow Residual Risk Report",
    "",
    "## Blocking Risks",
    "",
    "- Real public repo creation, public remote configuration, push, tag, and release remain not authorized.",
]
if clean_source_blocks_release:
    risk_lines.append("- Operational clean source was not asserted in check mode; rerun from a clean committed worktree before any real publication.")
risk_lines.extend([
    "",
    "## Boundary Notes",
    "",
    "- Repo mode evidence comes from the local throwaway repo under `target/logicflow-open-source-release-dry-run/public-repo`.",
    "- Snapshot mode and repo mode are both internal dry-run evidence; neither is a publication action.",
    "- Release checklist and residual risk report are internal artifacts and are not copied into the public snapshot or local public repo commit.",
])
residual_risk_report.write_text("\n".join(risk_lines) + "\n")
PY
}

write_failed_reports() {
  local exit_code="$1"
  local failed_command="$2"
  local generated_at
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  python3 - "${JSON_REPORT}" "${MARKDOWN_REPORT}" "${RELEASE_CHECKLIST}" "${RESIDUAL_RISK_REPORT}" "${generated_at}" "${SOURCE_MODE}" "${CURRENT_WORKTREE_CLEAN}" "${CLEAN_CHECKOUT_STATUS}" "${DIRTY_SOURCE_PROBE_STATUS}" "${PREREQUISITE_INPUTS_STATUS}" "${PUBLIC_REPO_COMMIT_COUNT}" "${CURRENT_STEP}" "${exit_code}" "${failed_command}" <<'PY'
import json
import sys
from pathlib import Path

json_report = Path(sys.argv[1])
markdown_report = Path(sys.argv[2])
release_checklist = Path(sys.argv[3])
residual_risk_report = Path(sys.argv[4])
generated_at = sys.argv[5]
source_mode = sys.argv[6]
current_worktree_clean = sys.argv[7] == "true"
clean_checkout_status = sys.argv[8]
dirty_source_probe_status = sys.argv[9]
prerequisite_inputs_status = sys.argv[10]
commit_count = int(sys.argv[11])
current_step = sys.argv[12]
exit_code = sys.argv[13]
failed_command = sys.argv[14]

blocking_items = [
    {
        "source": "release_runner",
        "item": f"Release dry-run failed at {current_step}",
        "blocks": "technical_dry_run_status",
    },
    {
        "source": "pending_owner_review",
]
if clean_checkout_status != "passed":
    blocking_items.append({
        "source": "clean_source_gate",
        "item": "Operational clean source was not asserted",
        "blocks": "release_governance_status",
    })

report = {
    "release_dry_run": "logicflow-open-source-release-dry-run",
    "mode": "local-dry-run",
    "generated_at": generated_at,
    "source": {
        "mode": source_mode,
        "current_worktree_clean": current_worktree_clean,
        "clean_checkout": clean_checkout_status,
        "dirty_source_probe": dirty_source_probe_status,
        "source_ref_recorded_in_internal_report": False,
    },
    "public_repo_dry_run": {
        "path": "target/logicflow-open-source-release-dry-run/public-repo",
        "git_initialized": commit_count > 0,
        "commit_count": commit_count,
        "commit_metadata_scan": "not-passed",
        "public_remote_added": False,
        "push": False,
        "tag": False,
        "release": False,
    },
    "statuses": {
        "technical_dry_run_status": "failed",
        "release_governance_status": "blocked",
        "publication_authorization_status": "not-authorized",
    },
    "failure": {
        "step": current_step,
        "exit_code": exit_code,
        "command": failed_command,
    },
    "consumed_reports": {
        "governance_gate_report": "tmp/testing-evidence/open-source/logicflow-open-source-governance-gates/governance-gate-report.json",
        "sync_report": "tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/sync-report.md",
        "patch_import_report": "tmp/testing-evidence/open-source/logicflow-public-sync-pipeline/patch-import-report.md",
    },
    "reports": {
        "release_checklist": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/release-checklist.md",
        "residual_risk": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/residual-risk-report.md",
        "markdown": "tmp/testing-evidence/open-source/logicflow-open-source-release-dry-run/release-dry-run-report.md",
    },
    "blocking_items": blocking_items,
    "publication_performed": {
        "public_repo_created": False,
        "public_remote_added": False,
        "push": False,
        "tag": False,
        "release": False,
    },
}
json_report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n")

lines = [
    "---",
    "doc_type: logicflow-open-source-release-dry-run-report",
    "roadmap: logicflow-open-source-extraction",
    "feature: 2026-07-26-logicflow-open-source-release-dry-run",
    "technical_dry_run_status: failed",
    "release_governance_status: blocked",
    "publication_authorization_status: not-authorized",
    f"updated: {generated_at}",
    "---",
    "",
    "# LogicFlow Open Source Release Dry Run Report",
    "",
    "## Status",
    "",
    "- Technical dry-run status: `failed`",
    "- Release governance status: `blocked`",
    "- Publication authorization status: `not-authorized`",
    f"- Failed step: `{current_step}`",
    f"- Exit code: `{exit_code}`",
    "- Public remote / push / tag / release: no",
    "",
    "## Blocking Items",
    "",
]
for item in blocking_items:
    lines.append(f"- `{item['blocks']}`: {item['item']} ({item['source']})")
markdown_report.write_text("\n".join(lines) + "\n")

checklist_lines = [
    "# LogicFlow Release Checklist",
    "",
    "| Area | Status | Evidence |",
    "|---|---|---|",
    f"| Required inputs | {prerequisite_inputs_status} | release manifest and referenced manifests/scripts |",
    f"| Clean source | {'passed' if clean_checkout_status == 'passed' else 'manual-review'} | clean checkout status `{clean_checkout_status}`, dirty source probe `{dirty_source_probe_status}` |",
    f"| Release runner | failed | failed at `{current_step}` with exit code `{exit_code}` |",
    "| Publication authorization | blocked | real public repo/remote/push/tag/release are not authorized |",
    "| No publication actions | passed | failure path keeps public repo creation, remote, push, tag, release false |",
]
release_checklist.write_text("\n".join(checklist_lines) + "\n")

risk_lines = [
    "# LogicFlow Residual Risk Report",
    "",
    "## Blocking Risks",
    "",
    f"- Release dry-run failed at `{current_step}` and must be rerun after fixing the failed command.",
    "- Real public repo creation, public remote configuration, push, tag, and release remain not authorized.",
]
if clean_checkout_status != "passed":
    risk_lines.append("- Operational clean source was not asserted; rerun from a clean committed worktree before any real publication.")
risk_lines.extend([
    "",
    "## Failure Detail",
    "",
    f"- Exit code: `{exit_code}`",
    f"- Command: `{failed_command}`",
])
residual_risk_report.write_text("\n".join(risk_lines) + "\n")
PY
}

on_error() {
  local exit_code="$1"
  local failed_command="$2"
  trap - ERR INT TERM
  set +e
  if [[ "${REPORT_FINALIZED}" != true ]]; then
    write_failed_reports "${exit_code}" "${failed_command}"
  fi
  echo "LogicFlow release dry-run failed at ${CURRENT_STEP}. Report: ${JSON_REPORT}" >&2
  exit "${exit_code}"
}

on_signal() {
  local signal_name="$1"
  trap - ERR INT TERM
  set +e
  if [[ "${REPORT_FINALIZED}" != true ]]; then
    write_failed_reports "128" "signal:${signal_name}"
  fi
  echo "LogicFlow release dry-run interrupted at ${CURRENT_STEP}. Report: ${JSON_REPORT}" >&2
  exit 128
}

trap 'on_error "$?" "$BASH_COMMAND"' ERR
trap 'on_signal INT' INT
trap 'on_signal TERM' TERM

CURRENT_STEP="required-inputs"
check_required_inputs
CURRENT_STEP="clean-source-gate"
run_clean_source_gate
CURRENT_STEP="public-snapshot-export"
prepare_public_snapshot
CURRENT_STEP="local-public-repo-dry-run"
init_local_public_repo
verify_local_public_repo
CURRENT_STEP="repo-mode-governance-gates"
run_repo_mode_governance_gates
CURRENT_STEP="public-build-sync-verification"
run_public_repo_build_and_sync_checks
CURRENT_STEP="write-release-reports"
write_release_reports
REPORT_FINALIZED=true

echo "LogicFlow release dry-run completed. Report: ${JSON_REPORT}"
