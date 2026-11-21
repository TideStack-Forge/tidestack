#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <TestClass> [TestClass...]" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
cd "${repo_root}"

test_selector="$(IFS=,; echo "$*")"

mvn \
  -pl :ouroboros-dev-project-generation,:ouroboros-util-zip,:ouroboros-dev-app-management \
  -am \
  -Dtest="${test_selector}" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

for test_class in "$@"; do
  report="$(find . -path '*/target/surefire-reports/TEST-*.xml' -name "TEST-*${test_class}.xml" | head -n 1)"
  if [ -z "${report}" ]; then
    echo "Missing surefire report for ${test_class}" >&2
    exit 1
  fi

  python3 - "${report}" <<'PY'
import sys
import xml.etree.ElementTree as ET

report = sys.argv[1]
root = ET.parse(report).getroot()
tests = int(root.attrib.get("tests", "0"))
if tests <= 0:
    raise SystemExit(f"{report} did not execute any tests")
PY
done
