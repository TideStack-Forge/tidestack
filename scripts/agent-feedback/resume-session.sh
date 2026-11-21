#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1])


def run(*args):
    return subprocess.check_output(args, cwd=root, text=True).strip()


branch = run("git", "branch", "--show-current") or "HEAD"
head = subprocess.run(["git", "rev-parse", "--verify", "--quiet", "HEAD"], cwd=root, capture_output=True, text=True)
if head.returncode not in (0, 1):
    raise RuntimeError(head.stderr.strip() or "Cannot inspect repository HEAD")

payload = {
    "branch": branch,
    "recentCommits": run("git", "log", "-5", "--oneline").splitlines() if head.returncode == 0 else [],
    "gitStatus": run("git", "status", "--short").splitlines(),
    "suggestedNextSteps": [
        "Read the current task, PR, or Issue record for goals, decisions, evidence, pending work, and blockers.",
        "Check whether current git status matches the unfinished subtask.",
        "If runtime validation is needed, run check-health.sh and query-logs.sh before editing.",
    ],
}

print(json.dumps(payload, ensure_ascii=False, indent=2))
PY
