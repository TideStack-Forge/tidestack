#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: import-public-data-model-pr.sh --public-repo <path> --public-pr <ref|fixture> --target-branch <branch> --manifest <path> [--dry-run]

Validates a public data-model PR import boundary and writes an import report.
This command never merges, pushes, publishes, releases, deploys, or contacts a
remote.
USAGE
}

public_repo=""
public_pr=""
target_branch=""
manifest=""
dry_run="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --public-repo)
      public_repo="${2:-}"
      shift 2
      ;;
    --public-pr)
      public_pr="${2:-}"
      shift 2
      ;;
    --target-branch)
      target_branch="${2:-}"
      shift 2
      ;;
    --manifest)
      manifest="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run="true"
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

if [[ -z "$public_repo" || -z "$public_pr" || -z "$target_branch" || -z "$manifest" ]]; then
  usage >&2
  exit 2
fi

python3 - "$public_repo" "$public_pr" "$target_branch" "$manifest" "$dry_run" <<'PY'
import datetime as _dt
import hashlib
import subprocess
import sys
from pathlib import Path


PUBLIC_REPO = Path(sys.argv[1]).resolve()
PUBLIC_PR = sys.argv[2]
TARGET_BRANCH = sys.argv[3]
MANIFEST = Path(sys.argv[4]).resolve()
DRY_RUN = sys.argv[5] == "true"
ROOT = Path.cwd().resolve()


def fail(message, code=1):
    print(message, file=sys.stderr)
    sys.exit(code)


def run(args, cwd=None, check=False):
    proc = subprocess.run(
        args,
        cwd=str(cwd or ROOT),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and proc.returncode != 0:
        fail(proc.stdout + proc.stderr)
    return proc


def scalar(value):
    value = value.strip()
    if not value:
        return ""
    if value == "[]":
        return []
    if value.startswith("[") and value.endswith("]"):
        body = value[1:-1].strip()
        if not body:
            return []
        return [scalar(part) for part in body.split(",")]
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        return value[1:-1]
    return value


def parse_manifest(path):
    sections = {}
    current = None
    current_item = None
    last_key = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        text = raw.strip()
        if indent == 0 and text.endswith(":"):
            current = text[:-1]
            sections.setdefault(current, [])
            current_item = None
            last_key = None
            continue
        if current is None:
            continue
        if indent == 2 and text.startswith("- "):
            body = text[2:]
            item = {}
            if ":" in body:
                key, value = body.split(":", 1)
                item[key.strip()] = scalar(value)
                last_key = key.strip()
            else:
                item["value"] = scalar(body)
                last_key = "value"
            sections[current].append(item)
            current_item = item
            continue
        if current_item is not None and indent >= 4:
            if text.startswith("- "):
                current_item.setdefault(last_key, [])
                if isinstance(current_item[last_key], list):
                    current_item[last_key].append(scalar(text[2:]))
                continue
            if ":" in text:
                key, value = text.split(":", 1)
                key = key.strip()
                current_item[key] = scalar(value)
                last_key = key
    return sections


def markdown_list(values):
    return "\n".join(f"- `{value}`" for value in values) or "- none"


def display_manifest():
    return "internal export manifest (not copied)"


def display_public_repo():
    return "public-tree-local-fixture"


def display_target_branch():
    return "import-branch-dry-run"


def path_allowed(path, allowed_roots):
    clean = path.strip("/")
    return any(clean == root or clean.startswith(root + "/") for root in allowed_roots)


if not PUBLIC_REPO.exists():
    fail(f"Public repo not found: {PUBLIC_REPO}")
if not (PUBLIC_REPO / ".git").exists():
    fail(f"Public repo is not a git worktree: {PUBLIC_REPO}")
if not MANIFEST.exists():
    fail(f"Manifest not found: {MANIFEST}")

manifest_hash = hashlib.sha256(MANIFEST.read_bytes()).hexdigest()
manifest = parse_manifest(MANIFEST)
included_paths = [item["path"] for item in manifest.get("included_modules", []) if item.get("path")]
generated_public_paths = [
    "README.md",
    "LICENSE",
    "docs",
    ".github/workflows/public-ci.yml",
    "core/ouroboros-data/README.md",
]
allowed_roots = sorted(set(included_paths + generated_public_paths))

public_ref = run(["git", "rev-parse", "HEAD"], cwd=PUBLIC_REPO, check=True).stdout.strip()
public_status = run(["git", "status", "--short"], cwd=PUBLIC_REPO, check=True).stdout.strip()
generated_at = _dt.datetime.now(_dt.timezone.utc).isoformat()

if PUBLIC_PR == "fixture":
    candidate_paths = [
        "core/ouroboros-data/ouroboros-data-core/PUBLIC_IMPORT_FIXTURE.md",
        "platform/internal-only/SHOULD_NOT_IMPORT.md",
    ]
    attribution = {
        "public_pr": "fixture",
        "author": "Fixture Public Contributor <fixture.public@example.invalid>",
        "commit_author": "Fixture Public Contributor <fixture.public@example.invalid>",
        "co_authors": [],
        "dco": "missing-fixture-evidence",
        "cla": "not-verified-fixture-evidence",
    }
else:
    status_lines = run(["git", "status", "--short"], cwd=PUBLIC_REPO, check=True).stdout.splitlines()
    candidate_paths = [line[3:].strip() for line in status_lines if len(line) > 3]
    attribution = {
        "public_pr": PUBLIC_PR,
        "author": "unknown-from-local-dry-run",
        "commit_author": run(["git", "log", "-1", "--format=%an <%ae>"], cwd=PUBLIC_REPO, check=True).stdout.strip(),
        "co_authors": [],
        "dco": "not-verified-local-dry-run",
        "cla": "not-verified-local-dry-run",
    }

allowed_candidates = [path for path in candidate_paths if path_allowed(path, allowed_roots)]
blocked_candidates = [path for path in candidate_paths if not path_allowed(path, allowed_roots)]
fixture_boundary_exercised = PUBLIC_PR == "fixture" and bool(allowed_candidates) and bool(blocked_candidates)
status = "passed" if allowed_candidates and (PUBLIC_PR == "fixture" or not blocked_candidates) else "failed"
if fixture_boundary_exercised:
    status = "passed"

report = f"""# Public PR Import Dry Run Report

## Summary

- Status: `{status}`
- Generated at: `{generated_at}`
- Public repo: `{display_public_repo()}`
- Public PR/ref: `{PUBLIC_PR}`
- Public ref recorded in public tree: `false`
- Target branch: `{display_target_branch()}`
- Manifest: `{display_manifest()}`
- Manifest SHA-256: `{manifest_hash}`
- Dry run: `{str(DRY_RUN).lower()}`
- Direct merge to master: `false`
- Remote actions: none

## Manifest-Owned Import Boundary

- Allowed roots:

{markdown_list(allowed_roots)}

- Candidate changed paths:

{markdown_list(candidate_paths)}

- Would import:

{markdown_list(allowed_candidates)}

- Blocked as out-of-boundary:

{markdown_list(blocked_candidates)}

## Attribution Evidence

- Public author: `{attribution['author']}`
- Commit author: `{attribution['commit_author']}`
- Co-authors: `{', '.join(attribution['co_authors']) if attribution['co_authors'] else 'none'}`
- DCO evidence: `{attribution['dco']}`
- CLA evidence: `{attribution['cla']}`
- Policy note: public imports record DCO evidence; CLA is informational historical evidence only, not a release gate.

## Non-Automatic Actions

- Branch creation: not performed.
- Direct merge: not performed.
- Remote push: not performed.
- Publish/release/deploy: not performed.

## Public Worktree Cleanliness

```text
{public_status or 'clean'}
```
"""
(PUBLIC_REPO / "import-report.md").write_text(report, encoding="utf-8")

state = f"""schema_version: 1
roadmap: dynamic-data-model-open-source
status: {status}
generated_at: "{generated_at}"
public_repo: {display_public_repo()}
public_pr: {PUBLIC_PR}
target_branch: {display_target_branch()}
manifest: internal-export-manifest
manifest_sha256: {manifest_hash}
public_ref_recorded_in_public_tree: false
dry_run: {str(DRY_RUN).lower()}
direct_merge_to_main: false
remote_actions_performed: false
allowed_paths:
"""
for path in allowed_candidates:
    state += f"  - {path}\n"
state += "blocked_paths:\n"
for path in blocked_candidates:
    state += f"  - {path}\n"
state += f"""attribution:
  author: "{attribution['author']}"
  commit_author: "{attribution['commit_author']}"
  co_authors: []
  dco: {attribution['dco']}
  cla: {attribution['cla']}
  owner_policy_decision_required: true
non_automatic_actions:
  branch_creation: false
  merge: false
  remote_push: false
  publish: false
  release: false
  deploy: false
"""
(PUBLIC_REPO / "public-import-state.yaml").write_text(state, encoding="utf-8")

print(f"public import report: {PUBLIC_REPO / 'import-report.md'}")
print(f"public import state: {PUBLIC_REPO / 'public-import-state.yaml'}")
print(f"status: {status}")
print(f"would import paths: {len(allowed_candidates)}")
print(f"blocked paths: {len(blocked_candidates)}")
print("remote actions: none")

if status != "passed":
    sys.exit(1)
PY
