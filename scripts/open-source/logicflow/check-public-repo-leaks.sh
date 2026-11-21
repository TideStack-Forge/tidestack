#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
DRY_RUN_DIR="${REPO_ROOT}/target/logicflow-public-repo-dry-run"
STRICT_CLEAN=false
ALLOW_GIT_DIR=false

usage() {
  cat <<'USAGE'
Usage: check-public-repo-leaks.sh [--dry-run-dir DIR] [--strict-clean] [--allow-git-dir]

Checks a generated LogicFlow public repo dry-run for forbidden internal paths,
imports, Maven artifacts, Git ancestry, and internal commit markers.

Use --strict-clean immediately after bootstrap generation to fail if Maven
target outputs are present. Without it, target directories are ignored so the
same leak check can be run after mvn test.

Use --allow-git-dir only for the local public repo dry-run created under target/.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run-dir)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --dry-run-dir" >&2
        exit 2
      fi
      DRY_RUN_DIR="$2"
      shift 2
      ;;
    --strict-clean)
      STRICT_CLEAN=true
      shift
      ;;
    --allow-git-dir)
      ALLOW_GIT_DIR=true
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

if [[ "${DRY_RUN_DIR}" != /* ]]; then
  DRY_RUN_DIR="${REPO_ROOT}/${DRY_RUN_DIR}"
fi

if [[ ! -d "${DRY_RUN_DIR}" ]]; then
  echo "Missing dry-run directory: ${DRY_RUN_DIR}" >&2
  exit 1
fi

failures=0

fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

require_path() {
  local path="$1"
  if [[ ! -e "${DRY_RUN_DIR}/${path}" ]]; then
    fail "Missing required public surface path: ${path}"
  fi
}

for required in \
  pom.xml \
  README.md \
  README.en.md \
  CONTRIBUTING.md \
  CONTRIBUTING.en.md \
  LICENSE \
  NOTICE \
  docs/adapter-integration-guide.md \
  docs/adapter-integration-guide.en.md \
  docs/data-model-integration.md \
  docs/data-model-integration.en.md \
  OPEN_SOURCE_SNAPSHOT.md \
  OPEN_SOURCE_SNAPSHOT.en.md \
  .github/workflows/build.yml \
  ouroboros-logicflow-bom/pom.xml \
  ouroboros-logicflow-core/pom.xml \
  ouroboros-logicflow-builtin-nodes/pom.xml \
  ouroboros-logicflow-authoring/pom.xml \
  ouroboros-logicflow-data-model/pom.xml \
  samples/script-node-standalone/pom.xml; do
  require_path "${required}"
done

if [[ "${ALLOW_GIT_DIR}" != true ]] && find "${DRY_RUN_DIR}" -type d -name .git -print -quit | grep -q .; then
  fail "Dry-run contains .git ancestry"
fi

for forbidden in \
  .codestable \
  frontends \
  platform \
  dev-module \
  app-module \
  security \
  ouroboros-logicflow-extra-nodes \
  ouroboros-logicflow-runtime \
  ouroboros-logicflow-dev \
  ouroboros-logicflow-web-adapter \
  ouroboros-logicflow-all; do
  if [[ -e "${DRY_RUN_DIR}/${forbidden}" ]]; then
    fail "Dry-run contains forbidden path: ${forbidden}"
  fi
done

if [[ "${STRICT_CLEAN}" == true ]] && (cd "${DRY_RUN_DIR}" && find . -path '*/target/*' -print -quit) | grep -q .; then
  fail "Dry-run contains target output"
fi

if ! grep -R -n --exclude-dir=.git "Apache License" "${DRY_RUN_DIR}/LICENSE" >/dev/null; then
  fail "LICENSE does not contain Apache License text"
fi

if ! grep -R -n --exclude-dir=.git "ouroboros-logicflow-bom" "${DRY_RUN_DIR}/samples/script-node-standalone/pom.xml" >/dev/null; then
  fail "Consumer sample does not import ouroboros-logicflow-bom"
fi

if grep -R -n --exclude-dir=.git "<parent>" "${DRY_RUN_DIR}/samples/script-node-standalone/pom.xml" >/dev/null; then
  fail "Consumer sample must not inherit the public root parent"
fi

if ! grep -R -n --exclude-dir=.git "script" \
  "${DRY_RUN_DIR}/samples/script-node-standalone/src/test/java" >/dev/null; then
  fail "ScriptNode sample evidence is missing"
fi

for forbidden_import in \
  'com\.ouroboros\.dev' \
  'com\.ouroboros\.web' \
  'com\.ouroboros\.security' \
  'com\.ouroboros\.websocket' \
  'com\.ouroboros\.mq' \
  'com\.ouroboros\.message' \
  'com\.ouroboros\.modelmanage' \
  'com\.ouroboros\.vcs' \
  'com\.ouroboros\.exception' \
  'com\.ouroboros\.metadata' \
  'com\.ouroboros\.model'; do
  if grep -R -n -E --exclude-dir=.git --exclude-dir=target "${forbidden_import}" "${DRY_RUN_DIR}" \
      --include='*.java' --include='*.xml' --include='*.json' >/dev/null; then
    fail "Dry-run contains forbidden import/prefix pattern: ${forbidden_import}"
  fi
done

if grep -R -n -E --exclude-dir=.git --exclude-dir=target --exclude-dir=ouroboros-logicflow-data-model \
    'com\.ouroboros\.data' "${DRY_RUN_DIR}" --include='*.java' --include='*.xml' --include='*.json' >/dev/null; then
  fail "Dry-run contains data-model imports outside ouroboros-logicflow-data-model"
fi

for forbidden_artifact in \
  ouroboros-dev-model-manage-webapi-scaffold \
  ouroboros-message-queue-core \
  ouroboros-web-security-core \
  ouroboros-websocket-core \
  common-app \
  common-dev \
  common-web \
  common-exception \
  common-metadata \
  common-model \
  common-script \
  ouroboros-util-excel \
  ouroboros-logicflow-extra-nodes \
  ouroboros-logicflow-runtime \
  ouroboros-logicflow-dev \
  ouroboros-logicflow-web-adapter \
  ouroboros-logicflow-all; do
  if grep -R -n --exclude-dir=.git --exclude-dir=target "<artifactId>${forbidden_artifact}</artifactId>" "${DRY_RUN_DIR}" --include='pom.xml' >/dev/null; then
    fail "Dry-run contains forbidden Maven artifact: ${forbidden_artifact}"
  fi
done

if ! python3 - "${DRY_RUN_DIR}" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
violations = []
for module in root.glob("ouroboros-logicflow-*"):
    for source in (module / "src/main").rglob("*.java"):
        if "com.ouroboros.expression" in source.read_text():
            violations.append(str(source.relative_to(root)))
    pom = ET.parse(module / "pom.xml").getroot()
    for dependency in pom.findall("p:dependencies/p:dependency", namespace):
        if (dependency.findtext("p:groupId", namespaces=namespace) == "com.ouroboros"
                and dependency.findtext("p:artifactId", namespaces=namespace) == "common-expression"
                and dependency.findtext("p:scope", namespaces=namespace) != "test"):
            violations.append(str(module.relative_to(root) / "pom.xml"))
if violations:
    print("common-expression is test support only: " + ", ".join(violations), file=sys.stderr)
    sys.exit(1)
PY
then
  fail "LogicFlow production modules must use expression ports"
fi

if find "${DRY_RUN_DIR}" -name pom.xml -not -path '*/target/*' -not -path '*/.git/*' -not -path '*/ouroboros-logicflow-data-model/pom.xml' -not -path '*/ouroboros-logicflow-bom/pom.xml' -not -path "${DRY_RUN_DIR}/pom.xml" \
    -exec grep -H -n '<artifactId>ouroboros-data-core</artifactId>' {} + | grep -q .; then
  fail "Only ouroboros-logicflow-data-model and public BOM/root may manage or depend on ouroboros-data-core"
fi

if grep -R -n -E --exclude-dir=.git --exclude-dir=target '\b[0-9a-f]{40}\b' \
    "${DRY_RUN_DIR}/README.md" \
    "${DRY_RUN_DIR}/NOTICE" \
    "${DRY_RUN_DIR}/OPEN_SOURCE_SNAPSHOT.md" \
    "${DRY_RUN_DIR}/pom.xml" \
    "${DRY_RUN_DIR}/.github" \
    "${DRY_RUN_DIR}/samples" >/dev/null; then
  fail "Public metadata contains a 40-character hex marker that could expose an internal commit"
fi

if [[ ${failures} -gt 0 ]]; then
  echo "LogicFlow public repo leak check failed with ${failures} issue(s)." >&2
  exit 1
fi

echo "LogicFlow public repo leak check passed: ${DRY_RUN_DIR}"
