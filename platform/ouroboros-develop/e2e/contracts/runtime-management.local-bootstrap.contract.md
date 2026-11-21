# Runtime Management Local Bootstrap Contract

```yaml
contract_id: platform-develop.runtime-management.local-bootstrap
version: 1
status: active-draft
owner: platform/ouroboros-develop
execution_mode: human-or-llm
supported_mode: local-classpath-backend-plus-webpack-dev-server
primary_backend: docker
backend_port: 18080
frontend_port: 18081
hand_off_contract: runtime-management.smoke.contract.md
```

## 1. Intent

This contract defines how to bootstrap the supported local environment for the develop-platform runtime-management browser smoke.

It exists to answer one question:

Can a human or large model prepare matching worktree artifacts, bring up the local backend and frontend, and then hand control to the browser smoke contract? Historical branch evidence does not certify the current checkout.

## 2. Scope

This contract covers only the following local mode:

- local develop backend launched in classpath mode
- local frontend launched by `scripts/webpack-serve.js`
- Docker-backed runtime-management flow

An executable helper currently exists for this contract:

- `scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh`

This contract does not define:

- Kubernetes browser bootstrap
- a generalized orchestration framework
- IDE-specific launch flows
- CI-specific container orchestration

The helper is an implementation of this contract, not a replacement for it.

## 3. Preconditions

The executor must confirm all of the following before attempting bootstrap:

### 3.1 Workspace context

- the working tree root is the target branch workspace
- the workspace contains `platform/ouroboros-develop/target/classes`
- the workspace contains `frontends/web/main-app/node_modules`

### 3.2 Canonical external config

Resolve `DEV_E2E_DEBUG_CONFIG_FILE` to an existing operator-provided debug configuration. The helper defaults through `DEV_E2E_PRIMARY_REPO_ROOT` (its fallback is the original author's primary checkout), so other machines and isolated worktrees should set the config path explicitly. Configuration may be external; compiled classes and dependency jars must match the target worktree.

If the resolved file is missing, this bootstrap mode is not ready.

### 3.3 Canonical backend classpath

The currently verified backend launch depends on:

- `tmp/playwright-it/backend/runtime.classpath`

If this file is missing, this bootstrap mode is not ready.

### 3.4 Reactor artifact alignment

The currently verified backend classpath is not a pure source-tree classpath.

It resolves most module dependencies from the local Maven repository referenced by:

- `${HOME}/.m2/repository`

Because of that, the executor must treat backend bootstrap as unsafe when the worktree contains backend-module changes that have not been installed to the local repository.

Observed failure mode:

- the source tree contains the new implementation
- `runtime.classpath` still resolves an older installed jar from `${HOME}/.m2/repository`
- Spring startup fails with dependency mismatches that do not reflect the current source tree

Executor rule:

- if relevant backend modules changed after the last local install, refresh the local installed artifacts before bootstrap
- if the minimal affected reactor slice is unclear, use the safe fallback command:

```bash
mvn -pl :ouroboros-develop -am -DskipTests -Djacoco.skip=true install
```

Observed failure mode:

- `-DskipTests` alone can still fail during `install` when an upstream module picks up an existing `jacoco.exec` and enforces coverage on the install path

Classpath refresh rule:

- after the install step, refresh the backend dependency classpath from the installed artifacts
- do not use reactor mode for this step

```bash
mvn -pl :ouroboros-develop -DincludeScope=runtime dependency:build-classpath \
  -Dmdep.outputFile=tmp/playwright-it/bootstrap/runtime.classpath.dependencies.txt
```

Observed failure mode:

- `mvn -pl :ouroboros-develop -am -DincludeScope=runtime dependency:build-classpath` omitted runtime transitive jars such as `ouroboros-business-model-default`
- backend startup then failed with `NoClassDefFoundError` even though `dependency:tree` still showed the dependency

### 3.5 External infrastructure

The currently verified local mode assumes:

- Docker API is reachable at `tcp://localhost:2375`
- MySQL is reachable as `mysql-latest:3306`

These are not optional for the verified Docker browser flow.

## 4. Required Inputs

The executor should resolve the following values:

```yaml
required_inputs:
  WORKTREE_ROOT: "current worktree root"
  PRIMARY_REPO_ROOT: "${DEV_E2E_PRIMARY_REPO_ROOT}: optional external config checkout"
  BACKEND_PORT: 18080
  FRONTEND_PORT: 18081
  SMOKE_APP_NAME: "playwrightit"
  SMOKE_PROFILE: "admin"
  SMOKE_RUNTIME_CONTAINER_NAME: "ouroboros-test-app-${SMOKE_APP_NAME}-${SMOKE_PROFILE}"
  BACKEND_DATA_ROOT: "${WORKTREE_ROOT}/tmp/playwright-it/backend/data"
  BACKEND_DOCUMENT_ROOT: "${WORKTREE_ROOT}/tmp/playwright-it/backend/document-libraries"
  BACKEND_CLASSPATH_FILE: "${WORKTREE_ROOT}/tmp/playwright-it/backend/runtime.classpath"
  BACKEND_PID_FILE: "${WORKTREE_ROOT}/tmp/playwright-it/backend/pids/backend.pid"
  BACKEND_LOG_FILE: "${WORKTREE_ROOT}/tmp/playwright-it/backend/logs/backend-live.log"
  FRONTEND_PID_FILE: "${WORKTREE_ROOT}/frontends/web/main-app/tmp/playwright-it/frontend/pids/frontend.pid"
  FRONTEND_LOG_FILE: "${WORKTREE_ROOT}/frontends/web/main-app/tmp/playwright-it/frontend/logs/frontend-live.log"
  DEBUG_CONFIG_FILE: "${DEV_E2E_DEBUG_CONFIG_FILE}: explicit existing config path"
```

## 5. Filesystem Preparation

Before launching processes, the executor should ensure the following directories exist:

- `tmp/playwright-it/backend/logs`
- `tmp/playwright-it/backend/pids`
- `frontends/web/main-app/tmp/playwright-it/frontend/logs`
- `frontends/web/main-app/tmp/playwright-it/frontend/pids`

The executor may reuse the existing `tmp/playwright-it/backend/data` and document-library roots.

The executor must not delete those roots automatically unless an explicit clean-room reset is intended.

Before launching processes, inspect the deterministic smoke container `${SMOKE_RUNTIME_CONTAINER_NAME}`. Remove it only after confirming it belongs to this task/run; if another task owns it, select an isolated app/profile instead. The helper's existing deterministic cleanup is not an ownership check.

Reason:

- the local bootstrap may intentionally reuse the backend data root
- Docker containers outlive backend restarts
- a previous failed smoke can leave `ouroboros-test-app-playwrightit-admin` behind even when `/api/app/container` no longer lists a child runtime
- without this cleanup, the next create step can fail with Docker `409 Conflict` on duplicate container name

## 6. Backend Bootstrap

### 6.1 Canonical launch mode

The manual backend launch shape is (resolve the variables first):

```bash
java -cp "$(cat "${BACKEND_CLASSPATH_FILE}")" \
  com.ouroboros.develop.OuroborosDevelopApplication \
  --server.port=${BACKEND_PORT} \
  --spring.config.additional-location=file:${DEBUG_CONFIG_FILE} \
  --spring.main.allow-bean-definition-overriding=true \
  --spring.main.allow-circular-references=true \
  --root-path=${BACKEND_DATA_ROOT} \
  --ouroboros.document-management.storage.filesystem.base-path=${BACKEND_DOCUMENT_ROOT} \
  --ouroboros.document-management.storage.filesystem.temp-path=${BACKEND_DOCUMENT_ROOT}/tmp \
  --ouroboros.security.external-auth.publicBaseUrl=http://127.0.0.1:${FRONTEND_PORT}
```

### 6.2 Backend launch recording

The executor should:

- redirect stdout and stderr to `${BACKEND_LOG_FILE}`
- record the launched pid in `${BACKEND_PID_FILE}`

### 6.3 Backend minimal readiness

The backend should be considered minimally ready only when all of the following are true:

- a process is listening on `${BACKEND_PORT}`
- backend log contains a matching Tomcat startup marker for `${BACKEND_PORT}` (the helper accepts current and legacy port log formats)
- backend log contains `Started OuroborosDevelopApplication`
- `POST /api/auth/login` returns HTTP `200`
- the login payload contains success code `0`

### 6.4 Backend dependency readiness

The backend should be considered smoke-ready only when Docker-backed runtime management is also responsive.

Current acceptable readiness probe:

- after authenticating, `GET /api/app/container/images?backendType=DOCKER` returns HTTP `200` and payload code `0`

If this probe fails, the backend is not ready for the runtime-management browser smoke, even if auth works.

### 6.5 Known mismatch signature

One real cold-start failure already observed in this branch was:

- backend source expected `FieldDefineMetadataAdapter`
- `${HOME}/.m2/repository` still supplied an older `ouroboros-dev-helper-dev-metadata-provider` jar containing `FieldTypeDefinitionMetadataAdapter`
- Spring failed during startup with a missing `@Qualifier("fieldTypeManagerServiceImpl")`

If a similar mismatch appears, the executor should suspect stale installed reactor artifacts before changing launch flags or readiness probes.

## 7. Frontend Bootstrap

### 7.1 Canonical launch mode

The manual frontend command is:

```bash
node scripts/webpack-serve.js \
  --api http://127.0.0.1:${BACKEND_PORT} \
  --debug_console http://127.0.0.1:${BACKEND_PORT} \
  --logicflow_editor http://127.0.0.1:${BACKEND_PORT} \
  --workflow_editor http://127.0.0.1:${BACKEND_PORT} \
  --other http://127.0.0.1:${BACKEND_PORT} \
  --host 127.0.0.1 \
  --port ${FRONTEND_PORT}
```

The working directory for this command is:

- `${WORKTREE_ROOT}/frontends/web/main-app`

### 7.2 Frontend launch recording

The executor should:

- redirect stdout and stderr to `${FRONTEND_LOG_FILE}`
- record the launched pid in `${FRONTEND_PID_FILE}`

### 7.3 Frontend readiness

The frontend should be considered ready only when all of the following are true:

- a process is listening on `${FRONTEND_PORT}`
- frontend log contains `Project is running at:`
- frontend log contains `Loopback: http://127.0.0.1:${FRONTEND_PORT}/`
- frontend log does not contain webpack or TypeScript compile markers such as `ERROR in` or `Failed to compile`
- `GET /` returns HTTP `200`
- the returned HTML includes `ouroboros-mothership-app`

The executor should also give the frontend a short stabilization window after the first successful root probe.

Reason:

- webpack dev server can emit `Project is running at:` before the first full compile finishes
- the root page can return HTML even when the subsequent compile ends with TypeScript errors
- without a stabilization window, bootstrap can produce a false green result and hand an unusable frontend to the next smoke step

### 7.4 Helper defaults and optional editors

The helper defaults to backend `18080` and frontend `18081`; override them with `DEV_E2E_BACKEND_PORT` and `DEV_E2E_FRONTEND_PORT`. It installs the affected reactor and refreshes the dependency classpath by default. Do not symlink compiled classes or classpath files from another worktree as a shortcut.

`DEV_E2E_LOGICFLOW_EDITOR_ENABLED` and `DEV_E2E_WORKFLOW_EDITOR_ENABLED` enable optional local editor servers (default ports `8084` and `8083`). Their readiness and proxy targets must be checked when enabled. This contract's manual frontend example uses the backend as a simple proxy target; the helper also supports explicit micro-frontend target overrides.

## 8. Readiness Caveats

### 8.1 Do not use `GET /login` as the only frontend probe

In the currently verified mode, direct HTTP probing of:

- `GET /login`

may return `404` even though browser navigation can still reach the login route through the frontend application.

Because of that, the safer HTTP readiness probe is:

- `GET /`

### 8.2 Do not trust pid files more than live probes

Pid files are helpful hints, but they are not the source of truth.

If pid files disagree with:

- listening ports
- process command lines
- readiness probes

the live probes win.

## 9. Tolerated Bootstrap Noise

The following observations are tolerated unless they block readiness or smoke execution:

- frontend Browserslist staleness warnings
- frontend webpack deprecation warnings
- frontend `MaxListenersExceededWarning`
- backend menu-tree `MenuModelCenter` errors that occur outside the runtime-management smoke path

These should be recorded as observations, not treated as automatic bootstrap failure.

## 10. Shutdown Rules

### 10.1 Stop priority

The executor should stop processes in this order:

1. frontend
2. optional workflow/logicflow editor servers started by this run
3. backend

### 10.2 Stop method

Preferred shutdown behavior:

- if a pid file exists and the process command still matches the expected command family, send a normal termination signal
- if the pid file is stale, ignore the pid file and inspect port/process commands to identify this run’s owned process; do not terminate another task’s process

### 10.3 Cleanup boundaries

Default shutdown must not automatically delete:

- backend data roots
- document-library roots

Default shutdown may clean:

- stale pid files
- stale log files if an explicit new-run reset is requested

## 11. Clean-room Reset Rules

Clean-room reset is optional and must be treated as a stronger action than normal shutdown.

If the executor chooses a clean-room reset, it may:

- stop frontend and backend
- clear stale logs
- clear stale pid files

It should not delete backend data roots unless the operator explicitly wants to discard existing local runtime data.

## 12. Hand-off to the Smoke Contract

Once backend and frontend are both ready, the executor should hand off to:

- `runtime-management.smoke.contract.md`

The smoke contract then becomes the source of truth for:

- API fixture preparation
- browser actions
- lifecycle assertions
- evidence capture
- final cleanup validation

## 13. Failure Contract

Bootstrap must be considered failed if any of the following happens:

- debug config file is missing
- backend classpath file is missing
- backend port does not open
- backend login probe does not succeed
- Docker-backed image-management probe does not succeed
- frontend port does not open
- frontend root probe does not succeed

## 14. Future Extension Notes

Future work may add additional bootstrap contracts for:

- Kubernetes browser bootstrap
- gateway-mediated local bootstrap
- additional scripted bootstrap modes (the local helper already exists)

Those future contracts should not rewrite this one in place unless they actually replace the verified local mode described here.
