# Approval Workflow Engine Routing Decision

## Decision

Chosen default production path: `flowable`

`ouroboros-approval-workflow-runtime` and
`ouroboros-approval-workflow-qb-spring-boot-starter` must converge to a
single default engine: `flowable`.

## Rejected Path

The following shape is not an accepted merge target:

- `ouroboros-approval-workflow-runtime` depends on both `activiti6` and `flowable`
- `ouroboros-approval-workflow-qb-spring-boot-starter` depends on both `activiti6` and `flowable`
- default artifacts rely on vendor auto-configuration allowlists to suppress the inactive engine after both engines are already on the classpath

## Allowed Coexistence

The inactive engine may remain in the repository only as a non-default path:

- `activiti6` may remain buildable and contract-tested as its own module
- `activiti6` may remain available for explicit migration verification
- `activiti6` may not remain bundled into the default `runtime` or default starter artifacts

## Why This Decision

- The branch goal is to replace the current `Activiti6` execution layer with `Flowable`, not to formalize long-term dual-engine default packaging.
- The architecture remediation review identified dual-engine default artifacts as boundary leakage and unnecessary default complexity.
- Keeping `activiti6` buildable preserves migration rollback and compatibility verification without making every default consumer pay the dual-engine classpath cost.

## Consequences

- Default assembly tests must prove that `flowable` is the only engine present in the default runtime/starter path.
- Any remaining coexistence logic must move out of the default artifact path.
- If future work requires a deliberate dual-engine distribution, it must be introduced as a separate, explicitly named transitional artifact instead of reusing the default runtime/starter modules.
