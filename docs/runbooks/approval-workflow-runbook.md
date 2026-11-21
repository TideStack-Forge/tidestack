---
doc_type: runbook
status: current
tags: [approval-workflow, operations, troubleshooting, runbook]
---

# Approval Workflow Runbook

## Scope

This runbook covers operational checks for approval workflow design, deployment, and runtime execution. It does not replace API reference documentation or feature design documents.

## Key Runtime Surfaces

- Design/manage APIs: approval workflow definition create, update, deploy, offline, and detail.
- Runtime APIs: init form, start instance, task form, task action submit, timeline/history, diagram/detail.
- Statistics APIs: approval workflow statistics include flow, node, and approver duration insight through `/api/approval-workflow/statistics/duration`; the read side uses engine history, not a separate statistics table.
- Engines: The current runtime accepts only Flowable. Activiti6 sources live under legacy-compat and are outside the current runtime switch.
- Frontends: develop-shell hosts design/manage flows; runtime-shell hosts user task flows.
- Designer: `workflow-editor` React Flow micro frontend.

## Local Joint Debug

Use the approval design joint-debug bootstrap when validating design-time integration:

```bash
scripts/dev-runtime/bootstrap-approval-design-joint-debug-local.sh
```

Expected local topology:

- backend: `18880`
- main shell: `18081`
- approval workflow designer micro frontend: `18083`
- LogicFlow editor micro frontend: `18084`
- local Docker development platform: `http://127.0.0.1:88`

The LogicFlow editor process is part of the broader local debug topology. It is not the current approval-workflow-designer renderer path; the approval designer uses the `workflow-editor` React Flow micro frontend.

Do not silently reuse old `18081/18083/18084` frontend processes when debugging proxy or source issues.

## Health Checks

Use `/actuator/health` as the health source of truth:

- `200`: service is ready enough for browser/API checks
- `503`: lifecycle guard is still blocking or startup is incomplete
- unreachable through nginx: check `/actuator/` proxy configuration before blaming login APIs

Login APIs are diagnostic signals only; they are not health checks.

## Definition Lifecycle Checklist

Use this order when validating a definition created in the development platform and consumed by a runtime app:

1. Develop save writes `workflow/{name}.workflow.json` under the application metadata root.
2. Develop commit fixes the application metadata version; saved-but-uncommitted assets are not valid runtime evidence.
3. Mothership/runtime loads or refreshes the app with `AppContext.homePath` pointing at that metadata version.
4. `ouroboros-approval-workflow-runtime` discovers and validates `workflow/**/*.workflow.json`, then imports preset `WorkflowModel` records after model refresh.
5. Imported preset models must be `source=preset`, `adopted=false`, and not deployed; `processDefinitionId` stays empty until explicit deploy.
6. Deploy only through `POST /api/approval-workflow/definition/{id}/deploy`; develop save and runtime import must never call Flowable deploy.
7. For a higher preset version, an unadopted deployed model must move to `updated` and require deploy again; existing Flowable history remains queryable.

If a flow is missing at start time, debug in that order. Do not start by redeploying unless the runtime model is already present and `status` says deployment is required.

## Deployment Checklist

Before deploying an approval definition:

1. Confirm the definition is V2 or can be normalized by the composite definition codec.
2. Confirm `formType` and `formKey` are set when the process is bound to a business model.
3. Confirm candidate sources are resolvable in the target runtime.
4. Confirm condition fields exist when conditional routing is used.
5. Confirm form permission behavior matches the form type:
   - business-model binding: start form field control belongs to the business model form layer
   - default form: StartNode permission remains active
6. Confirm timeout policies on approval nodes use supported actions: `remind`, `autoApprove`, `autoReject`, or `transfer` with `transferTo`.
7. Confirm EndNode outcomes are intentional:
   - blank outcome resolves to `approved`
   - `rejected` drives rejected instance outcome and veto status projection
   - custom values are read-side display values only
8. Deploy through approval workflow definition deploy APIs, not develop runtime-management shortcuts.

## Instance Cleanup（实例清理）

When clearing test or broken instances:

1. Capture evidence first: definition id, process definition id, instance id, task ids, user ids, and current state.
2. Prefer product APIs or engine-supported cancellation paths.
3. If direct database cleanup is unavoidable in a local disposable environment, document every table touched and never use it as a production SOP.
4. After cleanup, verify:
   - no active tasks remain for the instance
   - timeline/history expectations are understood
   - subsequent start requests create a fresh instance

Never hide cleanup behind test fixtures that make product state look valid without exercising the real runtime path.

## Performance Watchpoints

Watch these areas during load or large workflow validation:

- candidate resolution fan-out
- task list queries by assignee/candidate
- timeline/history queries
- diagram/detail payload size
- repeated definition normalization or engine model reads
- duration statistics over large date windows
- browser rendering of large React Flow graphs

For slow task lists, capture query parameters, current user identity, candidate groups, and result counts before optimizing.

## Troubleshooting（故障排查）

### Definition Cannot Be Opened

- Check whether the stored document is V1 or V2.
- Confirm the composite codec can normalize it.
- Check browser console/pageerror in the designer.
- Confirm `workflow-editor` is registered by `src/registerComponent.js`.

### Designer Has Empty Candidate Or Field Lists

- Confirm the outer page passes explicit `conditionFieldSource`, `candidateSource`, and `formPermissionFieldSource`, or their API variants.
- Check unresolved template expressions in source API config.
- Check the API response shape can be normalized by the editor source contract.
- Do not add URL-guessing fallback logic in the editor.

### Deploy Fails

- Check definition JSON and graph validation first.
- Check engine-specific transpiler logs.
- Leave `ouroboros.workflow.approval.engine` unset or set it to `flowable`. Other values, including `activiti6`, are rejected by the current runtime; see [engine routing](../../core/ouroboros-approval-workflow/docs/engine-routing-decision.md).
- Confirm no removed node types such as Timer/Notice remain in current definitions.
- If timeout policy deployment fails, confirm `duration` is ISO-8601 and `transfer` has a non-empty `transferTo`.

### Start Fails

- Check `formType/formKey`.
- Check init-form response before start.
- Check required form data and task assignee payload.
- For business-model bound flows, verify business-model workflow binding uses `workflow.bindings[0].processId`.

### Task Action Fails

- Capture task id, action code, payload, current assignee, candidate info, and instance id.
- Check whether the current node is ApprovalNode or HandleNode.
- Check action-specific required fields such as transfer target, add-sign target users, or free-reject target node.
- Confirm transaction rollback does not leave partial task state.

### Duration Statistics Look Wrong

- Confirm `from` and `to` are ISO-8601 values and the range does not exceed 366 days.
- Confirm the target definition key exists in the selected engine.
- Compare instance counts against completed historic process instances for the same key and range.
- Compare node and approver averages against completed historic tasks; unfinished tasks are not part of the duration insight.
- User display names are resolved in the web layer, but aggregation identity is still `userId`.

### Browser E2E Fails

- Confirm shell URL and micro frontend entry map.
- Confirm lifecycle health is ready.
- Rebuild deterministic approval users before multi-user cases.
- Save trace, console/pageerror, network log, and backend logs under the evidence root.

## Evidence To Keep

- `/actuator/health` response
- definition JSON
- deploy API response
- init-form and start responses
- task-form and action responses
- timeline/history snapshots
- diagram/detail snapshots
- browser console/pageerror
- backend logs around deploy/start/action

## Escalation

Open an issue when:

- a current node type cannot round-trip through the definition codec
- Flowable and Activiti6 behavior diverge outside documented compatibility boundaries
- candidate or form-permission source contracts are ambiguous
- runtime task state cannot be explained from definition + task history
