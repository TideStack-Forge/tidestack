#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: check-public-release-hardening.sh --manifest <path> --public-tree <path>

Runs local release-readiness hardening checks against the generated public data
model tree. This command never pushes, merges, publishes, releases, deploys, or
contacts a remote.
USAGE
}

manifest=""
public_tree=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      manifest="${2:-}"
      shift 2
      ;;
    --public-tree)
      public_tree="${2:-}"
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

if [[ -z "$manifest" || -z "$public_tree" ]]; then
  usage >&2
  exit 2
fi

python3 - "$manifest" "$public_tree" <<'PY'
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path


MANIFEST = Path(sys.argv[1]).resolve()
PUBLIC_TREE = Path(sys.argv[2]).resolve()


def fail(message, code=1):
    print(message, file=sys.stderr)
    sys.exit(code)


def run(args, cwd=None, check=False):
    proc = subprocess.run(
        args,
        cwd=str(cwd or PUBLIC_TREE),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and proc.returncode != 0:
        fail(proc.stdout + proc.stderr)
    return proc


def markdown_list(values):
    return "\n".join(f"- `{value}`" for value in values) or "- none"


def iter_public_files(root):
    ignored_dirs = {".git", "target", "tmp", "logs", "log", "node_modules", "__pycache__"}
    ignored_suffixes = {".class", ".jar", ".zip", ".gz", ".png", ".jpg", ".jpeg", ".gif", ".ico"}
    for current, dirs, files in os.walk(root):
        dirs[:] = [name for name in dirs if name not in ignored_dirs]
        current_path = Path(current)
        for file_name in files:
            if any(file_name.endswith(suffix) for suffix in ignored_suffixes):
                continue
            yield current_path / file_name


if not MANIFEST.exists():
    fail(f"Manifest not found: {MANIFEST}", 2)
if not PUBLIC_TREE.exists():
    fail(f"Public tree not found: {PUBLIC_TREE}", 2)

manifest_hash = hashlib.sha256(MANIFEST.read_bytes()).hexdigest()
required_reports = [
    "export-report.md",
    "release-gate-report.md",
    "public-sync-state.yaml",
    "import-report.md",
    "public-import-state.yaml",
]
missing_reports = [rel for rel in required_reports if not (PUBLIC_TREE / rel).exists()]

secret_patterns = [
    ("private-key", re.compile(r"BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("assigned-sensitive-value", re.compile(r"(?im)^\s*(secret|password|token)\s*[:=]\s*['\"]?[A-Za-z0-9_./+=-]{8,}['\"]?\s*$")),
]
secret_matches = []
metadata_matches = []
metadata_patterns = [str(Path.home()), "/Users/", "/private/tmp/", ".codestable/", "codex/"]
metadata_regex_patterns = [
    ("commit-sha", re.compile(r"\b[0-9a-f]{40}\b")),
    ("public-sync-internal-ref-field", re.compile(r"(?m)^internal_ref:")),
    ("public-sync-public-ref-field", re.compile(r"(?m)^public_ref:")),
    ("dry-run-author-email", re.compile(r"public-export-dry-run@example\.invalid")),
]
support_leak_patterns = [
    "common-app",
    "common-script",
    "com.ouroboros.script",
    "SpringContextUtil",
]
support_leak_matches = []
for public_file in iter_public_files(PUBLIC_TREE):
    text = public_file.read_text(encoding="utf-8", errors="ignore")
    for label, pattern in secret_patterns:
        if pattern.search(text):
            secret_matches.append(f"{public_file.relative_to(PUBLIC_TREE)}: {label}")
    for pattern in metadata_patterns:
        if pattern and pattern in text:
            metadata_matches.append(f"{public_file.relative_to(PUBLIC_TREE)}: {pattern}")
    for label, pattern in metadata_regex_patterns:
        if pattern.search(text):
            metadata_matches.append(f"{public_file.relative_to(PUBLIC_TREE)}: {label}")
    for pattern in support_leak_patterns:
        if pattern in text:
            support_leak_matches.append(f"{public_file.relative_to(PUBLIC_TREE)}: {pattern}")

history_text = run(["git", "log", "--format=%an%n%ae%n%s%n%b"], cwd=PUBLIC_TREE, check=True).stdout
history_leaks = []
for pattern in ["feat(data):", "codex/", ".codestable", "/Users/", "/private/tmp/", "Public Export Dry Run", "public-export-dry-run@example.invalid"]:
    if pattern in history_text:
        history_leaks.append(pattern)
if re.search(r"\b[0-9a-f]{40}\b", history_text):
    history_leaks.append("commit-sha")

license_path = PUBLIC_TREE / "LICENSE"
license_placeholder = license_path.exists() and "License Pending Owner Decision" in license_path.read_text(encoding="utf-8", errors="ignore")
remote_actions = []
for rel in ["export-report.md", "public-sync-state.yaml", "import-report.md", "public-import-state.yaml"]:
    path = PUBLIC_TREE / rel
    if path.exists():
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "remote_actions_performed: true" in text or "Remote actions: performed" in text:
            remote_actions.append(rel)

blocking = secret_matches + metadata_matches + support_leak_matches + history_leaks + missing_reports + remote_actions
status = "passed" if not blocking else "failed"
report = f"""# Public Release Hardening Report

## Summary

- Status: `{status}`
- Manifest: `internal export manifest`
- Manifest SHA-256: `{manifest_hash}`
- Public tree: `public-tree-local-fixture`

## Secret Scan

- Matches: `{len(secret_matches)}`

{markdown_list(secret_matches)}

## Internal Metadata Scan

- Matches: `{len(metadata_matches)}`

{markdown_list(metadata_matches)}

## Internal Support Closure Scan

- Matches: `{len(support_leak_matches)}`

{markdown_list(support_leak_matches)}

## History Scan

- Internal-history indicators: `{len(history_leaks)}`

{markdown_list(history_leaks)}

## License Scan

- LICENSE placeholder present: `{str(license_placeholder).lower()}`
- Owner/legal decision required: `true`

## Required Reports

- Missing reports: `{len(missing_reports)}`

{markdown_list(missing_reports)}

## Remote Actions

- Performed: `{len(remote_actions)}`

{markdown_list(remote_actions)}

## Release Readiness Blocking List

{markdown_list(blocking)}
"""
(PUBLIC_TREE / "hardening-report.md").write_text(report, encoding="utf-8")

print(f"hardening report: {PUBLIC_TREE / 'hardening-report.md'}")
print(f"status: {status}")
print(f"secret matches: {len(secret_matches)}")
print(f"internal metadata matches: {len(metadata_matches)}")
print(f"internal support closure matches: {len(support_leak_matches)}")
print(f"history leaks: {len(history_leaks)}")
print(f"missing reports: {len(missing_reports)}")
print(f"remote actions performed: {len(remote_actions)}")

if status != "passed":
    sys.exit(1)
PY
