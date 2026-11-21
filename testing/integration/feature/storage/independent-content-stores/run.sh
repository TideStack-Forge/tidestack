#!/usr/bin/env bash
set -euo pipefail

storage_scenario_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PYTHONDONTWRITEBYTECODE=1
exec python3 "$storage_scenario_dir/verification.py" "$@"
