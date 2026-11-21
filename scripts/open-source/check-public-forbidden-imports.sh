#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: check-public-forbidden-imports.sh --manifest <path> --public-tree <path>

Manifest-scoped forbidden import and public API guard for the generated public
data-model tree. This command never contacts remotes.
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
import sys
from pathlib import Path


MANIFEST = Path(sys.argv[1]).resolve()
PUBLIC_TREE = Path(sys.argv[2]).resolve()


def fail(message, code=1):
    print(message, file=sys.stderr)
    sys.exit(code)


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


if not MANIFEST.exists():
    fail(f"Manifest not found: {MANIFEST}", 2)
if not PUBLIC_TREE.exists():
    fail(f"Public tree not found: {PUBLIC_TREE}", 2)

manifest_hash = hashlib.sha256(MANIFEST.read_bytes()).hexdigest()
manifest = parse_manifest(MANIFEST)
included = [item["path"] for item in manifest.get("included_modules", []) if item.get("path")]
scan_roots = [path for path in included if (PUBLIC_TREE / path).exists()]

platform_forbidden = [
    "com.ouroboros.security",
    "com.ouroboros.businessmodel",
    "com.ouroboros.app",
    "com.ouroboros.lifecycle.event",
    "com.ouroboros.expression",
    "com.ouroboros.script",
]
sql_only_forbidden = ["liquibase", "org.springframework.boot.autoconfigure.liquibase"]
docs_forbidden = ["DBCodingSequencer", "Snowflakes", "SnowflakePrimaryKeyGeneratorFactory"]

matches = []
for rel in scan_roots:
    root = PUBLIC_TREE / rel
    patterns = list(platform_forbidden)
    if not rel.endswith("ouroboros-data-typed-core"):
        patterns.append("com.ouroboros.exception")
    if rel.endswith("ouroboros-data-sql"):
        patterns.extend(sql_only_forbidden)
    for current, dirs, files in os.walk(root):
        dirs[:] = [name for name in dirs if name not in {"target", ".git", "tmp", "logs", "log", "__pycache__"}]
        for file_name in files:
            if not (file_name.endswith(".java") or file_name == "pom.xml"):
                continue
            file_path = Path(current) / file_name
            text = file_path.read_text(encoding="utf-8", errors="ignore")
            for pattern in patterns:
                if pattern in text:
                    matches.append(f"{file_path.relative_to(PUBLIC_TREE)}: {pattern}")

doc_matches = []
for rel in ["README.md", "docs", "core/ouroboros-data/README.md"]:
    path = PUBLIC_TREE / rel
    candidates = []
    if path.is_file():
        candidates = [path]
    elif path.is_dir():
        candidates = [p for p in path.rglob("*.md") if p.is_file()]
    for file_path in candidates:
        text = file_path.read_text(encoding="utf-8", errors="ignore")
        for pattern in docs_forbidden:
            if pattern in text:
                doc_matches.append(f"{file_path.relative_to(PUBLIC_TREE)}: {pattern}")

status = "passed" if not matches and not doc_matches else "failed"
report = f"""# Public Forbidden Import/API Guard Report

## Summary

- Status: `{status}`
- Manifest: `internal export manifest`
- Manifest SHA-256: `{manifest_hash}`
- Public tree: `public-tree-local-fixture`

## Scan Roots

{markdown_list(scan_roots)}

## Forbidden Import Matches

- Count: `{len(matches)}`

{markdown_list(matches)}

## Public API/Docs Forbidden Mentions

- Count: `{len(doc_matches)}`

{markdown_list(doc_matches)}

## Runtime Adapter Note

Runtime adapter imports are not treated as public core/sql/sql-migration allowlists.
This guard scans only manifest-selected public roots and the SQL migration split module.
"""
(PUBLIC_TREE / "forbidden-import-report.md").write_text(report, encoding="utf-8")

print(f"forbidden import report: {PUBLIC_TREE / 'forbidden-import-report.md'}")
print(f"status: {status}")
print(f"forbidden import matches: {len(matches)}")
print(f"public docs forbidden matches: {len(doc_matches)}")

if status != "passed":
    sys.exit(1)
PY
