#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
MODULE_DIR="$ROOT_DIR/dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-kubernetes"
OUTPUT_DIR="${K8S_SMOKE_OUTPUT_DIR:-$ROOT_DIR/tmp/dev-runtime/k8s-smoke/$(date '+%Y%m%d-%H%M%S')}"
RUN_LOG="$OUTPUT_DIR/run.log"
RESULT_FILE="$OUTPUT_DIR/result.json"
DEPENDENCY_CP_FILE="$OUTPUT_DIR/classpath.txt"
JSHELL_SCRIPT_FILE="$OUTPUT_DIR/smoke-k8s-runtime.jsh"
KUBECTL_GET_FILE="$OUTPUT_DIR/kubectl-get.txt"
PODS_FILE="$OUTPUT_DIR/pods.yaml"
EVENTS_FILE="$OUTPUT_DIR/events.txt"
DESCRIBE_POD_FILE="$OUTPUT_DIR/describe-pod.txt"

K8S_SMOKE_NAMESPACE="${K8S_SMOKE_NAMESPACE:-dev-runtime-smoke}"
K8S_SMOKE_PVC="${K8S_SMOKE_PVC:-dev-runtime-pvc}"
K8S_SMOKE_INGRESS_SUFFIX="${K8S_SMOKE_INGRESS_SUFFIX:-smoke.local}"
K8S_SMOKE_TIMEOUT_SECONDS="${K8S_SMOKE_TIMEOUT_SECONDS:-120}"
K8S_SMOKE_KEEP_ON_FAILURE="${K8S_SMOKE_KEEP_ON_FAILURE:-false}"
K8S_SMOKE_CLEANUP="${K8S_SMOKE_CLEANUP:-true}"
K8S_SMOKE_IMAGE="${K8S_SMOKE_IMAGE:-}"

mkdir -p "$OUTPUT_DIR"
touch "$RUN_LOG"
exec > >(tee -a "$RUN_LOG") 2>&1

STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
FINISHED_AT=""
KUBE_CONTEXT=""
SCRIPT_EXIT_CODE=0
FAILURE_STAGE=""
FAILURE_REASON=""
FAILURE_RETAINED_RESOURCES="false"

CHECK_TEMP_CREATE_STATUS=""
CHECK_TEMP_RUNNING_STATUS=""
CHECK_TEMP_NODE_PORT=""
CHECK_TEMP_STOP_STATUS=""
CHECK_TEMP_START_STATUS=""
CHECK_TEMP_STARTED_RUNNING_STATUS=""
CHECK_TEMP_RESTART_STATUS=""
CHECK_TEMP_GENERATION=""
CHECK_TEMP_DELETE_STATUS=""
CHECK_STABLE_CREATE_STATUS=""
CHECK_STABLE_RUNNING_STATUS=""
CHECK_STABLE_ENDPOINT=""
CHECK_LEASE_EXPIRED_COUNT=""
CHECK_LEASE_AFTER_STATUS=""
CHECK_STABLE_DELETE_STATUS=""
CHECK_OK="false"

json_escape() {
  printf '%s' "$1" | awk 'BEGIN { ORS = "" } {
    gsub(/\\/, "\\\\");
    gsub(/"/, "\\\"");
    gsub(/\r/, "\\r");
    gsub(/\t/, "\\t");
    if (NR > 1) {
      printf "\\n";
    }
    printf "%s", $0;
  }'
}

json_string() {
  printf '"%s"' "$(json_escape "$1")"
}

json_nullable_string() {
  if [ -n "${1:-}" ]; then
    json_string "$1"
  else
    printf 'null'
  fi
}

json_bool() {
  if [ "${1:-false}" = "true" ]; then
    printf 'true'
  else
    printf 'false'
  fi
}

normalize_bool() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    true|1|yes)
      printf 'true'
      ;;
    false|0|no|'')
      printf 'false'
      ;;
    *)
      return 1
      ;;
  esac
}

write_result() {
  FINISHED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  {
    printf '{\n'
    printf '  "ok": %s,\n' "$(json_bool "$CHECK_OK")"
    printf '  "startedAt": %s,\n' "$(json_string "$STARTED_AT")"
    printf '  "finishedAt": %s,\n' "$(json_string "$FINISHED_AT")"
    printf '  "workspace": %s,\n' "$(json_string "$ROOT_DIR")"
    printf '  "outputDir": %s,\n' "$(json_string "$OUTPUT_DIR")"
    printf '  "kubeContext": %s,\n' "$(json_nullable_string "$KUBE_CONTEXT")"
    printf '  "namespace": %s,\n' "$(json_string "$K8S_SMOKE_NAMESPACE")"
    printf '  "image": %s,\n' "$(json_nullable_string "$K8S_SMOKE_IMAGE")"
    printf '  "artifacts": {\n'
    printf '    "log": %s,\n' "$(json_string "$RUN_LOG")"
    printf '    "dependencyClasspath": %s,\n' "$(json_string "$DEPENDENCY_CP_FILE")"
    printf '    "jshellScript": %s,\n' "$(json_string "$JSHELL_SCRIPT_FILE")"
    printf '    "kubectlGet": %s,\n' "$(json_nullable_string "$( [ -f "$KUBECTL_GET_FILE" ] && printf '%s' "$KUBECTL_GET_FILE" )")"
    printf '    "podsYaml": %s,\n' "$(json_nullable_string "$( [ -f "$PODS_FILE" ] && printf '%s' "$PODS_FILE" )")"
    printf '    "events": %s,\n' "$(json_nullable_string "$( [ -f "$EVENTS_FILE" ] && printf '%s' "$EVENTS_FILE" )")"
    printf '    "describePod": %s\n' "$(json_nullable_string "$( [ -f "$DESCRIBE_POD_FILE" ] && printf '%s' "$DESCRIBE_POD_FILE" )")"
    printf '  },\n'
    printf '  "checks": {\n'
    printf '    "tempCreate": %s,\n' "$(json_nullable_string "$CHECK_TEMP_CREATE_STATUS")"
    printf '    "tempRunning": %s,\n' "$(json_nullable_string "$CHECK_TEMP_RUNNING_STATUS")"
    printf '    "tempNodePort": %s,\n' "$(json_nullable_string "$CHECK_TEMP_NODE_PORT")"
    printf '    "tempStop": %s,\n' "$(json_nullable_string "$CHECK_TEMP_STOP_STATUS")"
    printf '    "tempStart": %s,\n' "$(json_nullable_string "$CHECK_TEMP_START_STATUS")"
    printf '    "tempStartedRunning": %s,\n' "$(json_nullable_string "$CHECK_TEMP_STARTED_RUNNING_STATUS")"
    printf '    "tempRestart": %s,\n' "$(json_nullable_string "$CHECK_TEMP_RESTART_STATUS")"
    printf '    "tempGeneration": %s,\n' "$(json_nullable_string "$CHECK_TEMP_GENERATION")"
    printf '    "tempDelete": %s,\n' "$(json_nullable_string "$CHECK_TEMP_DELETE_STATUS")"
    printf '    "stableCreate": %s,\n' "$(json_nullable_string "$CHECK_STABLE_CREATE_STATUS")"
    printf '    "stableRunning": %s,\n' "$(json_nullable_string "$CHECK_STABLE_RUNNING_STATUS")"
    printf '    "stableEndpoint": %s,\n' "$(json_nullable_string "$CHECK_STABLE_ENDPOINT")"
    printf '    "leaseExpiredCount": %s,\n' "$(json_nullable_string "$CHECK_LEASE_EXPIRED_COUNT")"
    printf '    "leaseAfter": %s,\n' "$(json_nullable_string "$CHECK_LEASE_AFTER_STATUS")"
    printf '    "stableDelete": %s\n' "$(json_nullable_string "$CHECK_STABLE_DELETE_STATUS")"
    printf '  },\n'
    printf '  "failure": '
    if [ -n "$FAILURE_STAGE" ] || [ -n "$FAILURE_REASON" ]; then
      printf '{\n'
      printf '    "stage": %s,\n' "$(json_nullable_string "$FAILURE_STAGE")"
      printf '    "reason": %s,\n' "$(json_nullable_string "$FAILURE_REASON")"
      printf '    "retainedResources": %s\n' "$(json_bool "$FAILURE_RETAINED_RESOURCES")"
      printf '  }\n'
    else
      printf 'null\n'
    fi
    printf '}\n'
  } > "$RESULT_FILE"
}

finish_and_exit() {
  SCRIPT_EXIT_CODE="$1"
  write_result
  printf 'Result written to %s\n' "$RESULT_FILE"
  exit "$SCRIPT_EXIT_CODE"
}

fail_with() {
  FAILURE_STAGE="$1"
  FAILURE_REASON="$2"
  CHECK_OK="false"
  finish_and_exit "$3"
}

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail_with "preflight" "required command not found: $1" 10
  fi
}

locate_bc_jar() {
  local artifact="$1"
  local path=""
  path="$(ls -1 "$HOME/.m2/repository/org/bouncycastle/$artifact"/*/"$artifact"-*.jar 2>/dev/null | tail -n 1 || true)"
  if [ -z "$path" ]; then
    fail_with "preflight" "missing local BouncyCastle jar: $artifact" 10
  fi
  printf '%s' "$path"
}

collect_evidence() {
  kubectl get all,ingress -n "$K8S_SMOKE_NAMESPACE" > "$KUBECTL_GET_FILE" 2>&1 || true
  kubectl get pods -n "$K8S_SMOKE_NAMESPACE" -o yaml > "$PODS_FILE" 2>&1 || true
  kubectl get events -n "$K8S_SMOKE_NAMESPACE" --sort-by=.lastTimestamp > "$EVENTS_FILE" 2>&1 || true
  local pod_name=""
  pod_name="$(kubectl get pods -n "$K8S_SMOKE_NAMESPACE" -l 'ouroboros.dev/runtime-app=smoke-k8s,ouroboros.dev/runtime-backend=KUBERNETES' -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [ -n "$pod_name" ]; then
    kubectl describe pod -n "$K8S_SMOKE_NAMESPACE" "$pod_name" > "$DESCRIBE_POD_FILE" 2>&1 || true
  else
    printf 'No matching runtime pod found.\n' > "$DESCRIBE_POD_FILE"
  fi
}

cleanup_resources() {
  kubectl delete deployment,service,ingress \
    -n "$K8S_SMOKE_NAMESPACE" \
    -l 'ouroboros.dev/runtime-app=smoke-k8s,ouroboros.dev/runtime-backend=KUBERNETES' \
    --ignore-not-found=true \
    --wait=true >/dev/null 2>&1
}

append_cp_path() {
  if [ -d "$1" ]; then
    if [ -n "${CLASSPATH_PREFIX:-}" ]; then
      CLASSPATH_PREFIX="$CLASSPATH_PREFIX:$1"
    else
      CLASSPATH_PREFIX="$1"
    fi
  fi
}

parse_result_markers() {
  while IFS= read -r line; do
    case "$line" in
      "SMOKE_RESULT "*)
        key="${line#SMOKE_RESULT }"
        value="${key#*=}"
        key="${key%%=*}"
        case "$key" in
          ok) CHECK_OK="$value" ;;
          failureStage) FAILURE_STAGE="$value" ;;
          failureReason) FAILURE_REASON="$value" ;;
          tempCreateStatus) CHECK_TEMP_CREATE_STATUS="$value" ;;
          tempRunningStatus) CHECK_TEMP_RUNNING_STATUS="$value" ;;
          tempNodePort) CHECK_TEMP_NODE_PORT="$value" ;;
          tempStopStatus) CHECK_TEMP_STOP_STATUS="$value" ;;
          tempStartStatus) CHECK_TEMP_START_STATUS="$value" ;;
          tempStartedRunningStatus) CHECK_TEMP_STARTED_RUNNING_STATUS="$value" ;;
          tempRestartStatus) CHECK_TEMP_RESTART_STATUS="$value" ;;
          tempGeneration) CHECK_TEMP_GENERATION="$value" ;;
          tempDeleteStatus) CHECK_TEMP_DELETE_STATUS="$value" ;;
          stableCreateStatus) CHECK_STABLE_CREATE_STATUS="$value" ;;
          stableRunningStatus) CHECK_STABLE_RUNNING_STATUS="$value" ;;
          stableEndpoint) CHECK_STABLE_ENDPOINT="$value" ;;
          leaseExpiredCount) CHECK_LEASE_EXPIRED_COUNT="$value" ;;
          leaseAfterStatus) CHECK_LEASE_AFTER_STATUS="$value" ;;
          stableDeleteStatus) CHECK_STABLE_DELETE_STATUS="$value" ;;
        esac
        ;;
    esac
  done < "$RUN_LOG"
}

K8S_SMOKE_KEEP_ON_FAILURE="$(normalize_bool "$K8S_SMOKE_KEEP_ON_FAILURE")" || fail_with "preflight" "invalid K8S_SMOKE_KEEP_ON_FAILURE value" 10
K8S_SMOKE_CLEANUP="$(normalize_bool "$K8S_SMOKE_CLEANUP")" || fail_with "preflight" "invalid K8S_SMOKE_CLEANUP value" 10

printf 'Kubernetes smoke output: %s\n' "$OUTPUT_DIR"

need_command kubectl
need_command mvn
need_command jshell

KUBE_CONTEXT="$(kubectl config current-context 2>/dev/null || true)"
if [ -z "$KUBE_CONTEXT" ]; then
  fail_with "preflight" "kubectl current-context is unavailable" 10
fi

if [ -z "$K8S_SMOKE_IMAGE" ]; then
  fail_with "preflight" "K8S_SMOKE_IMAGE is required" 10
fi

if ! kubectl get namespace "$K8S_SMOKE_NAMESPACE" >/dev/null 2>&1; then
  fail_with "preflight" "namespace not found: $K8S_SMOKE_NAMESPACE" 10
fi

PVC_PHASE="$(kubectl get pvc "$K8S_SMOKE_PVC" -n "$K8S_SMOKE_NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
if [ "$PVC_PHASE" != "Bound" ]; then
  fail_with "preflight" "pvc must exist and be Bound: $K8S_SMOKE_NAMESPACE/$K8S_SMOKE_PVC" 10
fi

BCPKIX_JAR="$(locate_bc_jar bcpkix-jdk18on)"
BCPROV_JAR="$(locate_bc_jar bcprov-jdk18on)"
BCUTIL_JAR="$(locate_bc_jar bcutil-jdk18on)"

printf 'Compiling runtime modules for smoke verification...\n'
if ! mvn -q -pl :ouroboros-dev-runtime-kubernetes -am test-compile; then
  fail_with "build" "maven test-compile failed for ouroboros-dev-runtime-kubernetes" 30
fi

printf 'Building dependency classpath...\n'
if ! mvn -q -pl :ouroboros-dev-runtime-kubernetes -am -DincludeScope=test dependency:build-classpath -Dmdep.outputFile="$DEPENDENCY_CP_FILE"; then
  fail_with "build" "failed to build dependency classpath" 30
fi

DEPENDENCY_CLASSPATH="$(tr -d '\n' < "$DEPENDENCY_CP_FILE")"
CLASSPATH_PREFIX=""
append_cp_path "$ROOT_DIR/libs/util/ouroboros-util/target/classes"
append_cp_path "$ROOT_DIR/libs/common/common-exception/target/classes"
append_cp_path "$ROOT_DIR/libs/common/common-app/target/classes"
append_cp_path "$ROOT_DIR/libs/common/common-dev/target/classes"
append_cp_path "$ROOT_DIR/ability/ouroboros-message-queue/ouroboros-message-queue-core/target/classes"
append_cp_path "$ROOT_DIR/dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/target/classes"
append_cp_path "$ROOT_DIR/dev-module/ouroboros-dev-helper/ouroboros-dev-helper-core/target/classes"
append_cp_path "$ROOT_DIR/dev-module/ouroboros-dev-helper/ouroboros-dev-helper-dev/target/classes"
append_cp_path "$ROOT_DIR/dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-kubernetes/target/classes"
append_cp_path "$ROOT_DIR/dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-kubernetes/target/test-classes"

FULL_CLASSPATH="$CLASSPATH_PREFIX:$DEPENDENCY_CLASSPATH:$BCPKIX_JAR:$BCPROV_JAR:$BCUTIL_JAR"
IMAGE_ESCAPED="$(printf '%s' "$K8S_SMOKE_IMAGE" | sed 's/\\/\\\\/g; s/"/\\"/g')"
NAMESPACE_ESCAPED="$(printf '%s' "$K8S_SMOKE_NAMESPACE" | sed 's/\\/\\\\/g; s/"/\\"/g')"
PVC_ESCAPED="$(printf '%s' "$K8S_SMOKE_PVC" | sed 's/\\/\\\\/g; s/"/\\"/g')"
INGRESS_SUFFIX_ESCAPED="$(printf '%s' "$K8S_SMOKE_INGRESS_SUFFIX" | sed 's/\\/\\\\/g; s/"/\\"/g')"
TIMEOUT_MILLIS=$((K8S_SMOKE_TIMEOUT_SECONDS * 1000))

cat > "$JSHELL_SCRIPT_FILE" <<EOF
import java.time.*;
import java.util.*;
import io.fabric8.kubernetes.client.*;
import com.ouroboros.dev.runtime.event.*;
import com.ouroboros.dev.runtime.kubernetes.backend.*;
import com.ouroboros.dev.runtime.kubernetes.config.*;
import com.ouroboros.dev.runtime.kubernetes.mapper.*;
import com.ouroboros.dev.runtime.kubernetes.status.*;
import com.ouroboros.dev.runtime.model.*;

String currentStage = "init";
long timeoutMillis = ${TIMEOUT_MILLIS}L;
String image = "${IMAGE_ESCAPED}";
String namespace = "${NAMESPACE_ESCAPED}";
String pvcName = "${PVC_ESCAPED}";
String ingressSuffix = "${INGRESS_SUFFIX_ESCAPED}";

void result(String key, Object value) {
  System.out.println("SMOKE_RESULT " + key + "=" + String.valueOf(value));
}

String sanitize(String value) {
  if (value == null) {
    return "";
  }
  return value.replace("\\r", " ").replace("\\n", " ").replace("=", "-");
}

KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
properties.setNamespace(namespace);
properties.setSharedPvcName(pvcName);
properties.setMountedWorkspaceRoot("/workspace");
properties.setIngressHostSuffix(ingressSuffix);

RuntimeSpec spec(String profile, ExposureStrategy exposureStrategy, Duration ttl) {
  return RuntimeSpec.builder()
      .appName("smoke-k8s")
      .profile(profile)
      .runtimeKind(RuntimeKind.TEST_APP)
      .backendType(BackendType.KUBERNETES)
      .workspaceMode(WorkspaceMode.PVC)
      .exposureStrategy(exposureStrategy)
      .image(image)
      .ttl(ttl)
      .workspaceRef(WorkspaceRef.builder()
          .workspaceMode(WorkspaceMode.PVC)
          .pvcName(pvcName)
          .namespace(namespace)
          .subPath("workspace/run/smoke-k8s/" + profile)
          .build())
      .build();
}

RuntimeInstance waitForStatus(KubernetesTestAppRuntimeBackend backend, RuntimeIdentityKey key, RuntimeStatus expected, long timeoutMs) throws Exception {
  long deadline = System.currentTimeMillis() + timeoutMs;
  RuntimeInstance last = null;
  while (System.currentTimeMillis() < deadline) {
    last = backend.getStatus(key).get();
    if (last.getStatus() == expected) {
      return last;
    }
    Thread.sleep(2000L);
  }
  throw new IllegalStateException("Timed out waiting for status " + expected + ", last=" + (last == null ? null : last.getStatus()));
}

RuntimeInstance waitForGeneration(KubernetesTestAppRuntimeBackend backend, RuntimeIdentityKey key, RuntimeStatus expected, long generation, long timeoutMs) throws Exception {
  long deadline = System.currentTimeMillis() + timeoutMs;
  RuntimeInstance last = null;
  while (System.currentTimeMillis() < deadline) {
    last = backend.getStatus(key).get();
    if (last.getStatus() == expected && Long.valueOf(generation).equals(last.getRuntimeGeneration())) {
      return last;
    }
    Thread.sleep(2000L);
  }
  throw new IllegalStateException("Timed out waiting for generation " + generation + ", lastStatus="
      + (last == null ? null : last.getStatus()) + ", lastGeneration=" + (last == null ? null : last.getRuntimeGeneration()));
}

void deleteQuietly(KubernetesTestAppRuntimeBackend backend, RuntimeIdentityKey key) {
  try {
    backend.delete(key).get();
  } catch (Throwable error) {
  }
}

try (KubernetesClient client = new KubernetesClientBuilder().build()) {
  KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(properties);
  KubernetesRuntimeStatusSynchronizer synchronizer = new KubernetesRuntimeStatusSynchronizer(client, properties, DevRuntimeEventPublisher.noop());
  KubernetesTestAppRuntimeBackend backend = new KubernetesTestAppRuntimeBackend(client, factory, synchronizer, properties);

  RuntimeSpec tempSpec = spec("bob", ExposureStrategy.TEMP_PORT, Duration.ofMinutes(30));
  RuntimeIdentityKey tempKey = RuntimeIdentityKey.from(tempSpec);
  RuntimeSpec stableSpec = spec("bob", ExposureStrategy.STABLE_DOMAIN, Duration.ofMinutes(30));
  RuntimeIdentityKey stableKey = RuntimeIdentityKey.from(stableSpec);
  RuntimeSpec leaseSpec = spec("lease", ExposureStrategy.TEMP_PORT, Duration.ofSeconds(5));
  RuntimeIdentityKey leaseKey = RuntimeIdentityKey.from(leaseSpec);

  deleteQuietly(backend, tempKey);
  deleteQuietly(backend, stableKey);
  deleteQuietly(backend, leaseKey);
  Thread.sleep(2000L);

  currentStage = "temp_create";
  RuntimeInstance tempCreated = backend.create(tempSpec).get();
  result("tempCreateStatus", tempCreated.getStatus());

  currentStage = "temp_running";
  RuntimeInstance tempRunning = waitForStatus(backend, tempKey, RuntimeStatus.RUNNING, timeoutMillis);
  String nodePort = tempRunning.getAccessRef().get("webNodePort");
  if (nodePort == null || nodePort.trim().isEmpty()) {
    throw new IllegalStateException("webNodePort missing on temp runtime");
  }
  result("tempRunningStatus", tempRunning.getStatus());
  result("tempNodePort", nodePort);

  currentStage = "temp_stop";
  RuntimeInstance tempStopped = backend.stop(tempKey).get();
  if (tempStopped.getStatus() != RuntimeStatus.STOPPED) {
    tempStopped = waitForStatus(backend, tempKey, RuntimeStatus.STOPPED, timeoutMillis);
  }
  result("tempStopStatus", tempStopped.getStatus());

  currentStage = "temp_start";
  RuntimeInstance tempStarted = backend.start(tempSpec).get();
  result("tempStartStatus", tempStarted.getStatus());
  RuntimeInstance tempStartedRunning = waitForStatus(backend, tempKey, RuntimeStatus.RUNNING, timeoutMillis);
  if (!Objects.equals(nodePort, tempStartedRunning.getAccessRef().get("webNodePort"))) {
    throw new IllegalStateException("nodePort changed after start");
  }
  result("tempStartedRunningStatus", tempStartedRunning.getStatus());

  currentStage = "temp_restart";
  RuntimeInstance tempRestart = backend.restart(tempSpec, 7L).get();
  result("tempRestartStatus", tempRestart.getStatus());
  RuntimeInstance tempRestartedRunning = waitForGeneration(backend, tempKey, RuntimeStatus.RUNNING, 7L, timeoutMillis);
  if (!Objects.equals(nodePort, tempRestartedRunning.getAccessRef().get("webNodePort"))) {
    throw new IllegalStateException("nodePort changed after restart");
  }
  result("tempGeneration", tempRestartedRunning.getRuntimeGeneration());

  currentStage = "temp_delete";
  backend.delete(tempKey).get();
  RuntimeInstance tempDeleted = waitForStatus(backend, tempKey, RuntimeStatus.NOT_FOUND, timeoutMillis);
  result("tempDeleteStatus", tempDeleted.getStatus());

  currentStage = "stable_create";
  RuntimeInstance stableCreated = backend.create(stableSpec).get();
  result("stableCreateStatus", stableCreated.getStatus());
  RuntimeInstance stableRunning = waitForStatus(backend, stableKey, RuntimeStatus.RUNNING, timeoutMillis);
  String stableEndpoint = stableRunning.getWebEndpoint();
  String expectedEndpoint = "https://smoke-k8s-bob." + ingressSuffix;
  if (!expectedEndpoint.equals(stableEndpoint)) {
    throw new IllegalStateException("unexpected stable endpoint: " + stableEndpoint);
  }
  result("stableRunningStatus", stableRunning.getStatus());
  result("stableEndpoint", stableEndpoint);

  currentStage = "lease_cleanup";
  backend.create(leaseSpec).get();
  KubernetesRuntimeLeaseCleaner cleaner = new KubernetesRuntimeLeaseCleaner(
      client,
      properties,
      Clock.offset(Clock.systemUTC(), Duration.ofMinutes(10))
  );
  List<RuntimeIdentityKey> expired = cleaner.cleanExpired();
  result("leaseExpiredCount", expired.size());
  RuntimeInstance leaseAfter = waitForStatus(backend, leaseKey, RuntimeStatus.NOT_FOUND, timeoutMillis);
  result("leaseAfterStatus", leaseAfter.getStatus());

  currentStage = "stable_delete";
  backend.delete(stableKey).get();
  RuntimeInstance stableDeleted = waitForStatus(backend, stableKey, RuntimeStatus.NOT_FOUND, timeoutMillis);
  result("stableDeleteStatus", stableDeleted.getStatus());

  result("ok", true);
  System.exit(0);
} catch (Throwable error) {
  error.printStackTrace(System.out);
  result("ok", false);
  result("failureStage", currentStage);
  result("failureReason", error.getClass().getSimpleName() + ": " + sanitize(error.getMessage()));
  System.exit(20);
}
EOF

printf 'Running real-cluster Kubernetes smoke flow...\n'
set +e
jshell --no-startup -s --class-path "$FULL_CLASSPATH" "$JSHELL_SCRIPT_FILE"
JSHELL_EXIT_CODE=$?
set -e

parse_result_markers

if [ "$JSHELL_EXIT_CODE" -ne 0 ] || [ "$CHECK_OK" != "true" ]; then
  if [ -z "$FAILURE_STAGE" ]; then
    FAILURE_STAGE="runtime"
  fi
  if [ -z "$FAILURE_REASON" ]; then
    FAILURE_REASON="jshell smoke execution failed"
  fi
  collect_evidence
  if [ "$K8S_SMOKE_CLEANUP" = "true" ] && [ "$K8S_SMOKE_KEEP_ON_FAILURE" != "true" ]; then
    cleanup_resources || true
  else
    FAILURE_RETAINED_RESOURCES="true"
  fi
  finish_and_exit 20
fi

if [ "$K8S_SMOKE_CLEANUP" = "true" ]; then
  if ! cleanup_resources; then
    FAILURE_STAGE="cleanup"
    FAILURE_REASON="smoke checks passed but cleanup failed"
    CHECK_OK="false"
    finish_and_exit 40
  fi
fi

finish_and_exit 0
