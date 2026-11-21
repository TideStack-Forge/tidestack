# Ouroboros Develop Browser E2E Contracts

This directory stores platform-owned browser E2E contracts for `ouroboros-develop`.

Current intent:

- define the browser smoke flow in a large-model-readable form first
- keep ownership with the develop platform instead of the generic frontend shell
- let future Playwright or other automation derive from the same contract later

Current round scope:

- contract-first only
- no permanent runner yet
- Docker browser flow is the active target
- Kubernetes browser flow remains a planned follow-up

Bootstrap note:

- the local bootstrap contract assumes `tmp/playwright-it/backend/runtime.classpath` and the local Maven repository are aligned
- if backend-side reactor modules changed, refresh installed artifacts before startup, or the backend may launch against stale jars from `${HOME}/.m2/repository`
- classpath refresh should use `mvn -pl :ouroboros-develop -DincludeScope=runtime dependency:build-classpath`; the reactor form with `-am` was observed to drop some runtime transitive jars in this repo
- when invoking `dependency:build-classpath` from a multi-module root, prefer an absolute `-Dmdep.outputFile=...`; relative output paths can land under the selected module and leave the canonical bootstrap classpath stale
- the helper now treats webpack or TypeScript compile markers such as `ERROR in` and `Failed to compile` as a frontend bootstrap failure instead of a passing startup

Executable helper:

- `bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh`
- this helper performs the conservative local bootstrap flow described by `runtime-management.local-bootstrap.contract.md`
- it authenticates with the local backend and verifies Docker-backed runtime readiness through `/api/app/container/images?backendType=DOCKER`
- it force-removes the deterministic smoke runtime container `ouroboros-test-app-playwrightit-admin` before startup so repeated local runs do not hit Docker name conflicts
- it writes a handoff summary and `tmp/playwright-it/bootstrap/result.json`

Source of truth:

- `contracts/runtime-management.smoke.contract.md`
- `contracts/runtime-management.local-bootstrap.contract.md`
