#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_SCRIPT="$SCRIPT_DIR/check-package-structure.sh"

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

mkdir -p \
  "$workdir/core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/service" \
  "$workdir/core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/scriptintelligence/model" \
  "$workdir/ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/java/com/ouroboros/organizationstructure/contextwrapper" \
  "$workdir/security/ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/contextwrapper"

cat <<'EOF_FILE' > "$workdir/core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/service/LogicFlowDevService.java"
package com.ouroboros.logicflow.service;

public class LogicFlowDevService {
}
EOF_FILE

cat <<'EOF_FILE' > "$workdir/core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/scriptintelligence/model/ScriptIntelligenceSnapshotRequest.java"
package com.ouroboros.logicflow.scriptintelligence.model;

public class ScriptIntelligenceSnapshotRequest {
}
EOF_FILE



cat <<'EOF_FILE' > "$workdir/security/ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/contextwrapper/SecurityContextWrapper.java"
package com.ouroboros.security.contextwrapper;

public class SecurityContextWrapper {
}
EOF_FILE

cat <<'EOF_BOUNDARIES' > "$workdir/package-structure-boundaries.tsv"
# rule_id	scope_root	expected_prefix	owner	note
PKG101	core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java	com.ouroboros.logicflow.dev	PKG-1.logicflow-dev	logicflow dev root must expose dev
PKG001	ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/java		PKG-1.organization-structure-wrapper	organization wrapper legacy package
PKG201	core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java		PKG-1.logicflow-dev	logicflow model transport cleanup
EOF_BOUNDARIES

cat <<'EOF_ALLOWLIST' > "$workdir/package-structure-allowlist.tsv"
# rule_id	scope_glob	owner	exit_condition	note
PKG201	core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/scriptintelligence/model/ScriptIntelligenceSnapshotRequest.java	PKG-1.logicflow-dev	remove when request dto leaves model	narrow model carve-out
EOF_ALLOWLIST

set +e
"$CHECK_SCRIPT" \
  --scan-root "$workdir" \
  --boundaries "$workdir/package-structure-boundaries.tsv" \
  --allowlist "$workdir/package-structure-allowlist.tsv" \
  >"$workdir/fail.out" 2>&1
status=$?
set -e

if [ "$status" -eq 0 ]; then
  echo "expected PKG101 violation for non-allowlisted logicflow root mismatch"
  exit 1
fi

if ! grep -q "PKG101" "$workdir/fail.out"; then
  echo "expected failure output to mention PKG101"
  cat "$workdir/fail.out"
  exit 1
fi

cat <<'EOF_ALLOWLIST' > "$workdir/package-structure-allowlist.tsv"
# rule_id	scope_glob	owner	exit_condition	note
PKG101	core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/**/*.java	PKG-1.logicflow-dev	remove when logicflow root converges	module-wide root carve-out
PKG201	core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/scriptintelligence/model/ScriptIntelligenceSnapshotRequest.java	PKG-1.logicflow-dev	remove when request dto leaves model	narrow model carve-out
EOF_ALLOWLIST

"$CHECK_SCRIPT" \
  --scan-root "$workdir" \
  --boundaries "$workdir/package-structure-boundaries.tsv" \
  --allowlist "$workdir/package-structure-allowlist.tsv" \
  >"$workdir/pass.out" 2>&1

if ! grep -q "0 violation" "$workdir/pass.out"; then
  echo "expected clean run summary"
  cat "$workdir/pass.out"
  exit 1
fi

if grep -q "SecurityContextWrapper" "$workdir/pass.out"; then
  echo "out-of-boundary security contextwrapper should not be scanned in first-wave guard"
  cat "$workdir/pass.out"
  exit 1
fi

echo "package structure guard tests passed"
