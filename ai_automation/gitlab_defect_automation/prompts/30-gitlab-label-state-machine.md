# GitLab Label State Machine

Use labels instead of paid workflow features so the process remains compatible with self-managed GitLab Free.

## 1. Label groups

### 1.1 Business state

- `state::incoming`
- `state::needs-info`
- `state::confirmed`
- `state::in-progress`
- `state::in-qa`
- `state::done`
- `state::closed-invalid`
- `state::closed-duplicate`

### 1.2 Human and agent gates

- `review::required`
- `agent::ready`
- `agent::claimed`
- `agent::mr-opened`
- `agent::blocked`
- `agent::done`

### 1.3 Classification

- `type::bug`
- `source::tencent-sheet`
- `source::customer`
- `source::internal`
- `sev::s1`
- `sev::s2`
- `sev::s3`
- `sev::s4`
- `prio::p1`
- `prio::p2`
- `prio::p3`
- `prio::p4`
- `domain::*`

## 2. Canonical flow

```text
incoming + review::required
  -> confirmed
  -> needs-info
  -> closed-invalid
  -> closed-duplicate

confirmed + agent::ready
  -> agent::claimed + in-progress
  -> agent::mr-opened + in-progress
  -> in-qa
  -> done
```

## 3. Transition matrix

| Action | Actor | Required current state | Apply | Remove |
| --- | --- | --- | --- | --- |
| create candidate | Intake | none | `type::bug`, `state::incoming`, `review::required`, source labels | none |
| confirm issue | Human | `state::incoming` | `state::confirmed` | `state::incoming`, `review::required` |
| request more info | Human | `state::incoming` or `state::confirmed` | `state::needs-info` | `state::incoming`, `state::confirmed`, `agent::ready` |
| mark invalid | Human | any pre-close state | `state::closed-invalid` | open business-state labels, `agent::*`, `review::required` |
| mark duplicate | Human | any pre-close state | `state::closed-duplicate` | open business-state labels, `agent::*`, `review::required` |
| grant agent execution | Human | `state::confirmed` | `agent::ready` | `agent::blocked`, `agent::done` |
| claim issue | Coding Dispatcher | `state::confirmed` plus `agent::ready` and no active lease | `agent::claimed`, `state::in-progress` | `agent::ready`, `agent::blocked`, `state::confirmed` |
| report blocked | Coding Result Reporter | `agent::claimed` or `state::in-progress` | `agent::blocked`, `state::confirmed` | `agent::claimed`, `agent::ready`, `state::in-progress` |
| report MR opened | Coding Result Reporter | `agent::claimed` plus active branch or MR | `agent::mr-opened`, `state::in-progress` | none |
| move to QA | Human or verified policy | `state::in-progress` or `agent::mr-opened` | `state::in-qa`, `agent::done` | `agent::claimed`, `agent::blocked`, `state::in-progress` |
| mark done | Human | `state::in-qa` | `state::done` | `state::in-qa`, `agent::ready`, `agent::mr-opened` |

## 4. Claim lease semantics

`agent::claimed` is not sufficient on its own. A valid claim should also have a
structured GitLab note or comment carrying at least:

- `run_id`
- `dispatcher_id`
- `worker_id`
- `claimed_at`
- `lease_expires_at`
- `branch_name` when known
- `mr_url` when known
- `supersedes_claim_id` when replacing an expired lease

Guardrails:

- do not reclaim when an unexpired lease exists
- do not silently overwrite expired claims; add a superseding claim note
- do not auto-reclaim issues that already have `agent::mr-opened` and an active
  MR unless a human resets them

## 5. Guardrails

- Do not mix business-state and agent-state meanings into one label.
- `agent::ready` must be set by humans, not by intake automation.
- `agent::claimed` must be set by the coding dispatcher or result reporter according to policy.
- `state::done` should remain a human-confirmed or verified state, not a default automatic outcome.
- `agent::blocked` and `agent::claimed` should not coexist.
- `agent::done` does not replace `state::done`.
