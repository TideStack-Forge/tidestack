# Automation Instance Examples

These examples illustrate environment bindings, not deployed instances. Resolve all example paths, sheet identities and verification commands before use; add the stable-record-key and fingerprint rules required by the shared schema. Scheduling is configured by the automation host.

## 1. Intake instance example

```yaml
instance_id: intake-customer-cn
instance_name: Customer Tencent Sheet Intake
schedule: every 15 minutes
secret_ref: vault://automation/intake/customer-cn
dry_run: false
max_items_per_run: 20
audit_sink: s3://automation-audit/defects/customer-cn

source:
  kind: tencent_sheet
  locator:
    document_id: doc-001
    sheet_id: sheet-a
    header_row: 1
    range: A:Z
  identity: customer_report_cn
  parse_profile:
    title_columns: [A]
    description_columns: [B, C, D]
    module_columns: [E]
    version_columns: [F]
    reporter_columns: [G]
    allow_cross_row_cluster: true
  change_cursor: incremental

gitlab:
  base_url: https://gitlab.example.com
  project: group/ouroboros
  issue_template: bug-report
  default_labels:
    - type::bug
    - state::incoming
    - review::required
    - source::tencent-sheet
    - source::customer

dedupe:
  policy: conservative
  thresholds:
    strong_match: 0.88
    medium_match: 0.65
    weak_match: 0.45

writeback:
  enabled: true
  columns:
    gitlab_issue_iid: H
    gitlab_issue_url: I
    sync_status: J
    primary_issue_iid: K
    relation_type: L
    intake_confidence: M
    last_synced_at: N
    last_sync_note: O

human_review:
  rule:
    require_review_for_new_issue: true
    require_review_for_medium_match: true
```

## 2. Coding dispatcher instance example

```yaml
instance_id: coding-monorepo-main
instance_name: Confirmed Defect Coding Dispatcher
schedule: every 30 minutes
secret_ref: vault://automation/coding/monorepo
dry_run: false
max_items_per_run: 5
audit_sink: s3://automation-audit/defects/coding

gitlab:
  base_url: https://gitlab.example.com
  project: group/ouroboros

issue_selector:
  labels_all:
    - type::bug
    - state::confirmed
    - agent::ready
  labels_none:
    - agent::claimed

claim_label: agent::claimed
claim_timeout_minutes: 90
dispatch_limit: 3
worker_profile: coding-worker-default
result_reporting_policy: standard-gitlab-comment
worktree_policy:
  root: /Users/songmingxu/Projects/ouroboros/.worktrees
  prefix: codex/
  cleanup: manual-after-review
```

## 3. Coding worker instance example

```yaml
instance_id: coding-worker-default
repo:
  path: /Users/songmingxu/Projects/ouroboros
  default_branch: dev
worktree:
  root: /Users/songmingxu/Projects/ouroboros/.worktrees
branch_prefix: codex/
verification:
  commands:
    - mvn test --file ./path/to/module/pom.xml -Dtest=TargetedTest
mr:
  target_branch: dev
  template: defect-fix-default
allowed_paths:
  - core/
  - ability/
  - frontends/web/
```
