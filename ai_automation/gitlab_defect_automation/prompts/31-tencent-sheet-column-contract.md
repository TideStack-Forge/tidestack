# Tencent Sheet Column Contract

The phase-1 source is Tencent Docs online sheet. This contract keeps the sheet useful for external collaboration while keeping GitLab as the only true defect system.

## 1. Recommended source columns

- `raw_title`
- `raw_description`
- `raw_reporter`
- `raw_report_time`
- `raw_module`
- `raw_version`
- `raw_priority_hint`
- `raw_customer`

These may be synthesized from one or more actual sheet columns through `source.parse_profile`.

## 2. Required writeback columns

- `gitlab_issue_iid`
- `gitlab_issue_url`
- `sync_status`
- `primary_issue_iid`
- `relation_type`
- `intake_confidence`
- `last_synced_at`
- `last_sync_note`

## 3. Recommended internal columns

- `source_record_id`
- `sync_fingerprint`

Semantics:

- `source_record_id`
  - stable external identity, never row number alone
- `sync_fingerprint`
  - fingerprint of the normalized raw input used to detect material change and
    suppress unchanged re-intake

## 4. Semantics

- `gitlab_issue_iid`
  - human-readable issue number such as `#123`
- `gitlab_issue_url`
  - direct link to the GitLab issue
- `sync_status`
  - recommended values:
    `created`, `linked_existing`, `needs_review`, `needs_info`,
    `suppressed_no_change`, `writeback_pending`, `writeback_retrying`,
    `writeback_failed`
- `primary_issue_iid`
  - the canonical issue if this row is a duplicate or supplement
- `relation_type`
  - `primary`, `duplicate`, or `supplement`
- `intake_confidence`
  - confidence score used for the routing decision
- `last_synced_at`
  - timestamp of the latest successful writeback
- `last_sync_note`
  - short explanation of the latest routing result

## 5. Rules

- Never rely on sheet row number as the only identity key.
- Never overwrite external raw input columns with system-generated text.
- When one cluster spans multiple rows, write the same primary issue reference to all involved rows.
- If GitLab write succeeds but sheet writeback fails, keep the GitLab result as
  authoritative and retry sheet writeback idempotently.
- Retrying writeback must not create or update GitLab issues again.
- Key each writeback event by `source_uid + routing_revision`; preserve the last correct issue reference during retries.
- Record pending, retrying and failed writeback states in audit storage and the configured status/note columns.
- `sync_fingerprint` represents the same normalized-input change signal as the audit `source_snapshot_hash`; keep its input field set stable.
