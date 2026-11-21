# Common Parameter Schema

Use these fields as prompt variables or automation metadata. Add project-specific fields only when they cannot be expressed through the shared schema.

## 1. Shared fields

| Field | Required | Description |
| --- | --- | --- |
| `instance_id` | yes | Stable ID for this automation instance |
| `instance_name` | yes | Human-readable automation name |
| `schedule` | yes | Run schedule configured outside the prompt |
| `secret_ref` | yes | Reference to credentials, never inline secrets |
| `dry_run` | no | If true, compute decisions but avoid side effects |
| `max_items_per_run` | no | Upper bound for one run |
| `audit_sink` | no | Audit destination or storage key |
| `operator_note` | no | Free-form note for this instance |

### Dry-run contract

`dry_run: true` applies to every stage and must be forwarded in dispatch payloads.
Read inputs and prepare proposed actions only: do not write GitLab or sheets, claim work,
change repository files, create branches/worktrees or MRs, launch executing workers, or persist audit changes.
Return planned actions and audit payloads in the result, clearly marked as simulated.
A run without `dry_run: true` follows the instance's normal execution policy.

## 2. Intake fields

| Field | Required | Description |
| --- | --- | --- |
| `source.kind` | yes | `tencent_sheet` in phase 1 |
| `source.locator` | yes | Sheet locator such as document ID, sheet ID, range, header row |
| `source.identity` | yes | Stable source identity such as `customer_report_cn` |
| `source.parse_profile` | yes | Row parsing and text synthesis rules |
| `source.change_cursor` | no | Incremental read cursor |
| `gitlab.base_url` | yes | Base URL of the GitLab instance |
| `gitlab.project` | yes | Target project ID or path |
| `gitlab.issue_template` | no | Preferred issue template |
| `gitlab.default_labels` | yes | Labels added to new candidate issues |
| `dedupe.policy` | yes | Recommended default: `conservative` |
| `dedupe.thresholds` | yes | Threshold object for strong, medium, and weak matches |
| `writeback.enabled` | yes | Whether the reporter should write back |
| `writeback.columns` | yes | Tencent sheet writeback column mapping |
| `human_review.rule` | yes | Conditions that require explicit human review |

## 3. Coding dispatcher fields

| Field | Required | Description |
| --- | --- | --- |
| `issue_selector` | yes | Query or label selector for ready issues |
| `claim_label` | yes | Usually `agent::claimed` |
| `claim_timeout_minutes` | yes | Claim expiration policy |
| `dispatch_limit` | yes | Maximum issues dispatched in one run |
| `worker_profile` | yes | Worker template and execution profile |
| `result_reporting_policy` | yes | Required result format and reporting fields |
| `worktree_policy` | yes | Worktree creation and cleanup policy |

## 4. Coding worker fields

| Field | Required | Description |
| --- | --- | --- |
| `repo.path` | yes | Repository path |
| `repo.default_branch` | yes | Default target branch |
| `worktree.root` | yes | Worktree root directory |
| `branch_prefix` | yes | Suggested prefix such as `codex/` |
| `verification.commands` | yes | Commands the worker must run before reporting |
| `mr.target_branch` | yes | MR target branch |
| `mr.template` | no | MR body template |
| `allowed_paths` | no | Path allowlist for safe file edits |

## 5. Structured cluster payload

The `Issue Writer Worker` should receive a cluster payload shaped like:

```json
{
  "cluster_id": "cluster-20260318-001",
  "source_identity": "customer_report_cn",
  "cluster_fingerprint": "<stable-cluster-fingerprint>",
  "dedupe_thresholds": {"strong_match": 0.88, "medium_match": 0.65, "weak_match": 0.45},
  "records": [
    {
      "source_record_id": "customer-a-defect-0042",
      "source_uid": "doc-001:sheet-a:customer-a-defect-0042",
      "source_snapshot_hash": "<hash-of-normalized-input>",
      "routing_revision": 1,
      "raw_excerpt": "Customer cannot save workflow after adding approver",
      "normalized_summary": "Save fails after adding approver node",
      "metadata": {
        "sheet_row": 18,
        "reported_by": "customer-a",
        "reported_at": "2026-03-18T09:30:00+08:00"
      }
    }
  ],
  "cluster_summary": {
    "title_candidate": "Workflow save fails after approver node changes",
    "symptoms": [
      "save action fails",
      "happens after approver node change"
    ],
    "modules": [
      "approval-workflow"
    ],
    "versions": [
      "develop-20260318"
    ]
  },
  "candidate_matches": [
    {
      "issue_iid": 321,
      "confidence": 0.61,
      "reason": "Similar module and symptom wording"
    }
  ],
  "routing_decision": {
    "mode": "create_candidate_issue",
    "confidence": 0.61,
    "reason": "Below auto-attach threshold"
  }
}
```

The writer must preserve record identities, hashes and routing revisions in `source_record_mappings`.
The reporter receives `source.locator` and resolves each current row by its stable record key;
`metadata.sheet_row` is a location hint and must be rechecked before writing.
The dispatcher loads prior audit bindings and suppresses unchanged records in `needs-info`,
`closed-invalid` or `closed-duplicate` states as specified in the design.

## 6. Structured coding context package

The `Coding Worker` should receive a context package shaped like:

```json
{
  "run_id": "coding-20260318-010",
  "issue": {
    "project": "group/ouroboros",
    "iid": 456,
    "title": "Workflow save fails after approver node changes",
    "description": "...",
    "labels": [
      "type::bug",
      "state::confirmed",
      "agent::ready"
    ]
  },
  "context": {
    "relevant_comments": [
      "Regression started after route editor change"
    ],
    "related_merge_requests": [],
    "suggested_paths": [
      "core/ouroboros-approval-workflow",
      "frontends/web/ouroboros-approval-workflow-designer"
    ],
    "verification_commands": [
      "mvn test --file ./path/to/module/pom.xml -Dtest=TargetedTest"
    ]
  }
}
```
