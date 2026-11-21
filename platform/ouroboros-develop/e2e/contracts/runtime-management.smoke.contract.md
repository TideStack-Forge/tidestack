# Runtime Management Browser Smoke Contract

```yaml
contract_id: platform-develop.runtime-management.browser-smoke
version: 1
status: active-draft
owner: platform/ouroboros-develop
execution_mode: human-or-llm
primary_backend: docker
planned_backends:
  - k8s
entry_page: /container-management
```

## 1. Intent

This contract defines the minimum browser-level smoke flow for the develop platform runtime-management experience.

It exists to answer one question:

Can a real user, through the develop platform UI, manage test-runtime lifecycle and image actions successfully against a live backend environment?

This contract is platform-specific. It is not a generic frontend-shell contract.

## 2. Scope

The smoke covered by this contract includes:

- API-based environment preparation
- UI login
- opening runtime management
- image management actions
- runtime creation
- runtime lifecycle actions
- final cleanup verification

This contract applies to the develop platform surface that owns:

- test application management
- image management
- runtime lifecycle management

## 3. Non-goals

This contract does not currently require:

- debug tunnel validation
- dev-helper browser flows
- post-launch business-page behavior inside the running app
- Kubernetes browser execution
- gateway-specific routing assertions
- zero console warnings
- zero legacy page noise

## 4. Required Environment

The executor must have a live environment already prepared.

This contract does not require the executor to decide how the environment is booted.

For the currently verified local startup mode, see:

- `runtime-management.local-bootstrap.contract.md`

Accepted boot styles include:

- classpath-based local backend startup
- jar-based startup
- Docker-based local startup
- future gateway-mediated startup

### 4.1 Canonical environment variables

The executor should resolve the following values before attempting the flow:

```yaml
required_env:
  E2E_BASE_URL: "develop platform web base url"
  E2E_API_BASE_URL: "develop platform api base url"
  E2E_USERNAME: "login username"
  E2E_PASSWORD: "login password"
  E2E_RUNTIME_BACKEND: "docker or k8s"
  E2E_APP_NAME: "smoke application name"
  E2E_APP_TITLE: "smoke application title"
optional_env:
  E2E_IMAGE_TAG: "expected base image tag when pinning a specific tag is needed"
  E2E_ARTIFACTS_DIR: "artifact output directory"
```

### 4.2 Current active assumptions

Current proven assumptions are:

- `E2E_RUNTIME_BACKEND=docker`
- the runtime-management page is reachable at `/container-management`
- the develop login page is reachable at `/login`
- a local environment can be bootstrapped through `runtime-management.local-bootstrap.contract.md`

## 5. Canonical Entities

The executor should reason in terms of the following entities:

- smoke application
- base image
- test runtime instance
- runtime state
- image-management dialog
- create-runtime dialog

The executor should not rely on raw Docker implementation concepts as universal platform concepts.

Examples:

- a host port is a backend-specific projection
- a container id is not the primary platform management object
- future Kubernetes flows should still operate on runtime instance semantics

## 6. Preflight Preparation Contract

Before starting browser interactions, the executor must complete all of the following:

### 6.1 Authenticate through API

Input:

- `E2E_USERNAME`
- `E2E_PASSWORD`

Success condition:

- a usable access token is obtained

### 6.2 Ensure the smoke application exists

Input:

- `E2E_APP_NAME`
- `E2E_APP_TITLE`

Success condition:

- the smoke application exists and can appear on the runtime-management page

### 6.3 Cleanup leftover runtime state

Input:

- `E2E_APP_NAME`
- active profile or default profile resolution of the current environment

Success condition:

- no leftover runtime instance for the smoke application remains before UI actions start

## 7. Browser Flow Contract

### 7.1 Login

Action:

- open `/login`
- submit the configured credentials

Success conditions:

- browser-side token storage exists
- authenticated navigation can reach `/container-management`

### 7.2 Open runtime management

Action:

- open `/container-management`

Success conditions:

- the smoke application row is visible
- the image-management entry is visible
- the create-runtime entry is visible

### 7.3 Image management: list

Action:

- open the image-management dialog

Success conditions:

- the dialog is visible
- at least one base image is listed
- the active base image tag or image reference is visible

### 7.4 Image management: pull by tag

Action:

- open the pull-by-tag flow
- submit a valid tag

Success conditions:

- the backend accepts the request successfully
- the UI remains in a usable state after completion

### 7.5 Image management: repull

Action:

- trigger repull for the listed base image
- confirm the action when confirmation is required

Success conditions:

- the backend accepts the request successfully

### 7.6 Create runtime

Action:

- open the create-runtime dialog
- submit the default or server-provided create form

Success conditions:

- the backend accepts the create request successfully
- a runtime identifier is returned or implied by the UI state
- the runtime eventually reaches `运行中`

### 7.7 Pause runtime

Action:

- trigger pause on the runtime row

Success conditions:

- the backend accepts the request successfully
- the runtime eventually reaches `已暂停`

### 7.8 Resume runtime

Action:

- trigger resume or unpause on the runtime row

Success conditions:

- the backend accepts the request successfully
- the runtime eventually reaches `运行中`

### 7.9 Stop runtime

Action:

- trigger stop on the runtime row

Success conditions:

- the backend accepts the request successfully
- the runtime eventually reaches `已停止`

### 7.10 Start runtime

Action:

- trigger start on the runtime row

Success conditions:

- the backend accepts the request successfully
- the runtime eventually reaches `运行中`

### 7.11 Stop runtime again

Action:

- trigger stop again before deletion

Success conditions:

- the backend accepts the request successfully
- the runtime eventually reaches `已停止`

### 7.12 Delete runtime

Action:

- trigger delete on the runtime row
- confirm the action when confirmation is required

Success conditions:

- the backend accepts the delete request successfully
- the runtime row eventually disappears from the UI

## 8. Final Verification Contract

After all browser actions, the executor must verify both views of cleanup:

### 8.1 UI cleanup

Success condition:

- the runtime row no longer exists on `/container-management`

### 8.2 API cleanup

Success condition:

- querying the runtime list for the smoke application shows zero child runtimes

## 9. Docker-specific Expectations

These expectations are active today.

### 9.1 Create dialog

The Docker create dialog may expose backend-specific port fields.

Current acceptable assertion:

- the web field exists and contains a numeric value

Current unacceptable assertion:

- hardcoding exact port numbers as part of the smoke contract
- expecting direct api or apiDebug port fields (backend API access uses tunnel sessions)

### 9.2 Access model

Current access model:

- Web port: directly exposed via host port mapping or NodePort
- Backend API port: accessible via on-demand tunnel session (similar to debug sessions)
- Debug port: accessible via separate debug tunnel session

The executor must not record direct port mapping for all ports as a universal contract of the platform.

## 10. Kubernetes Position

Kubernetes is reserved in this contract but not yet active as a required browser execution target.

Current rule:

- `E2E_RUNTIME_BACKEND=k8s` is a planned extension point
- the future Kubernetes browser flow must reuse runtime-instance semantics from this contract
- the future Kubernetes browser flow must not force Docker-only assumptions such as fixed port-triplet creation

## 11. Tolerated Environment Noise

The following issues are tolerated by this contract unless they directly break the runtime-management flow:

- login-adjacent menu-tree noise caused by `MenuModelCenter` classpath issues outside the tested page path
- `iconfont.js` 404 noise
- React 18 legacy warnings from existing UI libraries

These should be recorded as observations, but they are not by themselves smoke-failure conditions.

## 12. Evidence Contract

An execution that claims success should capture enough evidence for later inspection.

Recommended evidence:

- operation log
- relevant request and response summary
- before-actions screenshot
- after-actions screenshot
- final page url
- final cleanup confirmation from API

## 13. Failure Contract

The execution must be considered failed if any of the following happens:

- API preflight authentication fails
- the smoke application cannot be found or created
- image-management actions fail
- runtime creation fails
- any required runtime state transition does not converge in time
- runtime deletion fails
- final UI or API cleanup verification fails

## 14. Future Automation Notes

When a permanent runner is implemented later, it should map one-to-one to the top-level actions in this contract.

Recommended future mapping shape:

- one preparation module
- one authentication module
- one image-management module
- one runtime-lifecycle module
- one evidence recorder

The runner should be derived from this contract, not the other way around.

## 15. Backend API Tunnel Session (Optional Verification)

The smoke contract does not require backend API tunnel session verification as a mandatory step, but executors may optionally verify this capability.

### 15.1 Optional verification steps

If the executor chooses to verify backend API tunnel sessions:

1. Open backend API tunnel session via API
2. Verify `code=0` and retain `data.sessionId` and `data.endpoint`
3. Access backend API via tunnel endpoint
4. Verify connectivity and response
5. Close backend API tunnel session
6. Query the same session ID and verify it is closed or no longer available according to the session lifecycle

### 15.2 API endpoints

- Open session: `POST /api/dev/runtime/backend-api-sessions`
- Get session: `GET /api/dev/runtime/backend-api-sessions/{sessionId}`
- Close session: `DELETE /api/dev/runtime/backend-api-sessions/{sessionId}`

The open request body uses `OpenDebugSessionRequest` with `appName`, `profile` and the target runtime fields. Subsequent operations use the returned session ID; `endpoint` is a field of `DebugSessionDTO`.

### 15.3 Independence from debug sessions

Backend API tunnel sessions and debug tunnel sessions are independent:

- Both can exist simultaneously
- Closing one does not affect the other
- Each has its own lifecycle management
