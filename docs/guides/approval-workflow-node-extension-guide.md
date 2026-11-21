---
doc_type: developer-guide
status: current
tags: [approval-workflow, node-extension, handle-node, developer-guide]
---

# Approval Workflow Node Extension Guide

## Audience

This guide is for developers adding or changing approval workflow node types. It describes the product path, not historical compatibility paths.

## Current Node Set

The current approval workflow node set includes:

- `StartNode` / `发起节点`
- `ApprovalNode` / `审批节点`
- `HandleNode` / `办理节点`
- `CcNode` / `抄送节点`
- `SubflowNode` / `子流程节点`
- `JoinNode` / `合并节点`
- `EndNode` / `结束节点`

`HandleNode` is the reference example for a full-stack node extension because it touches the definition model, React Flow editor, engine transpilers, task form behavior, and engine testkit coverage.

The editor palette currently exposes approval, handle, cc, subprocess, join-or, and end entries. Start is created by initialization. Branching is represented by multiple `SEQUENCE` edges with optional edge conditions; branch and condition helper nodes are not current persisted node types.

## Extension Checklist

Add a new node only through the official definition and editor path:

1. Define the stable node contract.
2. Add backend definition model and codec support.
3. Add graph validation and structured graph behavior.
4. Add engine transpiler support for the Flowable default path and the Activiti6 compatibility path, or document the unsupported capability boundary in the engine adapter.
5. Add runtime task/form/action behavior if the node creates user work.
6. Add editor V2 and middle graph translator support.
7. Add React Flow adapter, palette, inspector, and validation support.
8. Add engine-testkit scenarios and per-engine tests.
9. Update docs and architecture references.

Do not add hidden registries, one-off editor-only node names, or engine-specific node contracts that bypass the canonical graph.

## Backend Definition Layer

Backend node contracts live under `core/ouroboros-approval-workflow/ouroboros-approval-workflow-core`.

Use these seams:

- definition node classes under `definition/nodes`
- canonical graph handling through `definition/graph/StructuredFlowDefinition`
- versioned JSON handling through the composite definition codec
- graph-level semantics through the existing semantics and validation classes

The node type stored in JSON must be stable. For example, the办理 node persists as `HandleNode` and is rendered as `办理节点` in the editor.

## Engine Transpilers

Each supported engine must either implement the node or explicitly document unsupported capability behavior in its own compatibility path.

Flowable is the default runtime path. Activiti6 is an explicit compatibility path. A node extension must check both paths before it is considered complete: definition support, transpiler processor behavior, registration/SPI wiring where applicable, and per-engine tests must either pass or carry a documented capability boundary.

For Flowable, `HandleNode` is implemented by a dedicated transpiler processor and runtime task-form behavior. Keep engine work behind engine adapter classes rather than leaking Flowable or Activiti concepts into the definition model. Use the same rule for Activiti6 compatibility work: engine-specific differences stay behind the engine adapter layer and do not change the canonical definition JSON.

When a node creates work for a human user, verify:

- task creation shape
- assignee or candidate resolution
- form key and form field permissions
- completion action semantics
- timeline or task event output

For approval task timeout behavior, keep the timeout policy on `ApprovalNode.timeout` rather than introducing a separate timer node. The current contract uses ISO-8601 `duration` plus an `action` of `remind`, `autoApprove`, `autoReject`, or `transfer`.

## Editor Integration

The editor path is React Flow only:

- add persisted node type mapping in `src/translator/define`
- map V2 to middle graph and back
- add React Flow node type mapping and insertion support
- add inspector behavior through the existing node editor seams
- add validation messages in `src/validation`
- keep page-level save/deploy/binding actions outside the editor

External lists such as candidates, condition fields, and form-permission fields must come from explicit `candidateSource`, `conditionFieldSource`, and `formPermissionFieldSource` inputs or their API variants. Do not infer them from the current URL or business-model naming conventions.

## Test Strategy

Minimum coverage for a node extension:

- codec or definition contract test for JSON input/output
- canonical graph validation test
- engine transpiler test for Flowable and Activiti6, or a documented unsupported capability boundary for the compatibility path
- engine-testkit scenario when runtime behavior is shared
- task-form/action test if the node creates user tasks
- editor translator round-trip test
- React Flow integration or component test for palette/inspector behavior

For `HandleNode`, the reference coverage includes Flowable transpiler tests, handle-node runtime contract tests, task-form exposure tests, and React Flow translator/editor paths.

## Documentation Updates

Update these documents when node semantics change:

- `core/ouroboros-approval-workflow/DESIGN.md`
- `core/ouroboros-approval-workflow/design/definition-metadata-and-graph-contracts/overview.md`
- `frontends/web/ouroboros-approval-workflow-designer/DESIGN.md`
- [审批流模块设计](../design/modules/core/ouroboros-approval-workflow.md)
- this guide

REST endpoint reference documentation is out of scope for node extension work unless the public API itself changes.

When terminal semantics change, document `EndNode.outcome` together with the runtime projection surface: normal EndNode traversal writes `__processOutcome`, instance detail/event payloads expose `outcome`, and business-model status projection special-cases only `approved` and `rejected`.

## Anti-Patterns

- Adding a node only to the editor palette.
- Adding engine-specific JSON that the canonical graph cannot parse.
- Creating a second renderer or G6 compatibility path.
- Reading candidates or form fields from implicit page state.
- Treating runtime task actions as editor-only configuration.
- Skipping engine-testkit coverage for shared runtime behavior.
