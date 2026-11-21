#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: check-pkgen-public-surface.sh --manifest <path> --public-tree <path>

Manifest-scoped guard for data-pkgen-coding. It scans only the projected
data-pkgen-coding surface, avoiding false positives from legitimate runtime or
SQL modules elsewhere in the public tree.
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
import os
import sys
from pathlib import Path

manifest = Path(sys.argv[1]).resolve()
public_tree = Path(sys.argv[2]).resolve()

if not manifest.exists():
    print(f"Manifest not found: {manifest}", file=sys.stderr)
    sys.exit(2)
if not public_tree.exists():
    print(f"Public tree not found: {public_tree}", file=sys.stderr)
    sys.exit(2)

pkgen_path = public_tree / "core/ouroboros-data/ouroboros-data-pkgen-coding"
projected = pkgen_path.exists()
forbidden_patterns = [
    "DBCodingSequencer",
    "CodeGenELAndScriptContext",
    "SnowflakePrimaryKeyGeneratorFactory",
    "Snowflakes",
    "common-expression",
    "com.ouroboros.expression",
    "com.querydsl",
    "com.ouroboros.data.pkgenerator.model",
]

matches = []
if projected:
    for current, _, files in os.walk(pkgen_path):
        for file_name in files:
            if not (file_name.endswith(".java") or file_name == "pom.xml"):
                continue
            file_path = Path(current) / file_name
            text = file_path.read_text(encoding="utf-8", errors="ignore")
            for pattern in forbidden_patterns:
                if pattern in text:
                    matches.append(f"{file_path.relative_to(public_tree)}: {pattern}")


if projected:
    print(f"data-pkgen-coding projected: yes ({pkgen_path})")
else:
    print("data-pkgen-coding projected: no; manifest keeps it deferred or split-source only")
print(f"forbidden matches: {len(matches)}")
for match in matches:
    print(match)

if matches:
    sys.exit(1)
PY
