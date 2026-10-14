# B6 — BUGGY Tasks / Activity / Proactive Automation — Implementation Report

**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Date:** 2026-10-04
**Foundation compatibility baseline:** `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
**Previous Buggy gate:** B5 Typed Tool Catalog — IMPLEMENTED / SOURCE-VERIFIED

---

## 1. Scope completed

B6 implements Buggy's durable work/orchestration layer without creating a second runtime authority.

Implemented:

- durable, revisioned Tasks independent from chat/session lifetime;
- task states including dependency/approval waiting states;
- task relations including parent/child, blocked-by and related links;
- cycle prevention for parent/child task graphs;
- Owner-readable Activity separate from Foundation Audit;
- canonical attention levels `INFO`, `NOTICE`, `ACTION_REQUIRED`, `URGENT`, `CRITICAL`;
- low-value activity bundling support and healthy-signal noise suppression;
- proactive levels `OBSERVE`, `ANALYZE`, `PREPARE`, `ACT`, `ESCALATE`;
- candidate signal model from BUGGYBOT-SPEC-V1;
- durable automation revisions and automation run history;
- signal-triggered and schedule-triggered automation definitions;
- B5 tool/capability/risk binding validation;
- hard `ACT == LOW + AUTONOMOUS_LOW` enforcement;
- privileged automation activation binding to Phase-9 Requests/Approvals;
- direct reuse of Foundation `SlidingWindowRateLimiter`;
- direct reuse of Foundation `SelfHealingBudgetTracker` / `SelfHealingPolicy` for cooldown, window budget, same-target suppression and attempt caps;
- Foundation `AuditRecorder` reuse for automation runs;
- high-risk/escalation audit fail-closed behavior;
- persistence migration `V100_004__create_buggy_tasks_activity_automation.sql`;
- JDBC adapters for Tasks, Activity and Automation;
- live PostgreSQL B6 regression harness.

No Tool Runtime, Request/Approval engine, Security authority, Audit system, Supervisor, Pipeline or scheduler thread was duplicated.

---

## 2. Durable Tasks

Task history is append-only. Each state change creates a new `BuggyTaskRevision`; a small mutable head points at the active revision.

Canonical states:

```text
OPEN
ACTIVE
WAITING_DEPENDENCY
WAITING_APPROVAL
PAUSED
COMPLETED
FAILED
CANCELLED
```

Tasks retain progress, attention, waiting reason and actor provenance. Terminal states cannot be reopened through the B6 service.

Relations:

```text
PARENT_CHILD
BLOCKED_BY
RELATED
```

Parent/child graph creation is bounded and rejects cycles.

---

## 3. Activity vs Audit

B6 preserves the canonical distinction:

```text
Activity = Owner-readable work history
Audit    = immutable security/compliance trail
```

Activity does not replace or weaken Foundation Audit.

Automation executions additionally emit through the existing `AuditRecorder`. HIGH-risk or escalated automation audit records are critical and therefore inherit the Foundation fail-closed audit rule.

Activity summaries/details pass the existing Foundation `TelemetrySanitizer` before persistence.

---

## 4. Proactive automation model

The product model is:

```text
OBSERVE → ANALYZE → PREPARE → ACT / ESCALATE
```

Healthy signals are intentionally ignored by the proactive runner to avoid notification noise.

Supported initial signal types are the candidates already fixed in BUGGYBOT-SPEC-V1:

- NetworkCore availability
- PostgreSQL health
- assistant health
- Minecraft server health
- deployment failure
- error-rate spike
- resource pressure
- backup verification
- certificate expiry
- pending approval
- stuck request
- recurring bug
- failed self-healing

B6 provides orchestration and persistence. Concrete privileged execution remains behind the pre-existing Foundation Tool/Request/Pipeline boundaries through a narrow `BuggyAutomationExecutionPort`.

---

## 5. Risk composition and B5 tool binding

Every automation binds to a concrete B5 `ToolId` and exact B3 capability.

B6 rejects:

- tools not present in `buggy-tools-v1`;
- capability/tool mismatches;
- automation risk below the B5 ToolDescriptor risk;
- `ACT` using any tool other than `LOW + AUTONOMOUS_LOW`.

Therefore a HIGH/CRITICAL outcome cannot be decomposed into smaller LOW actions to bypass approval.

---

## 6. Budgets / cooldowns / attempts / rate limits

B6 intentionally reuses Foundation primitives instead of creating an automation-specific duplicate:

- `SlidingWindowRateLimiter` for request-rate suppression;
- `SelfHealingBudgetTracker` + `SelfHealingPolicy` for global cooldown, same-target suppression, per-target attempt count and actions-per-window.

B6 additionally constrains `maxAttemptsPerTarget` to **1..3**, preserving the canonical default maximum of three retries from the post-Foundation handoff.

---

## 7. Privileged automation activation

LOW automations may be enabled directly when their B5 tool is explicitly autonomous LOW.

For MEDIUM/HIGH/CRITICAL automation definitions, `ENABLED` requires an `ApprovalRequestId`.

`FoundationRequestAutomationActivationGuard` then verifies against the existing Phase-9 repository that the request:

- exists;
- is exactly type `BUGGY_AUTOMATION_ACTIVATION`;
- is `APPROVED`, `EXECUTING` or `EXECUTED`;
- is not expired;
- has a risk classification covering the automation risk.

B6 does not self-approve or mutate request state.

---

## 8. Scheduling boundary

B6 stores schedule intervals and can evaluate which automation is due from its durable last-run state.

It intentionally does **not** start a second scheduler thread or scheduler service. Host/bootstrap scheduling may call the B6 due-evaluation entry point; B6 remains deterministic and durable while runtime scheduling ownership stays outside the product domain.

---

## 9. Persistence

Migration:

```text
V100_004__create_buggy_tasks_activity_automation.sql
```

Adds:

- `assistant.buggy_task_revisions`
- `assistant.buggy_task_head`
- `assistant.buggy_task_relations`
- `assistant.buggy_activity`
- `assistant.buggy_automation_revisions`
- `assistant.buggy_automation_head`
- `assistant.buggy_automation_runs`

Database triggers reject UPDATE/DELETE of task revisions, task relations, Activity, automation revisions and automation runs.

---

## 10. Verification

Local source gate:

- 539 main Java sources compile with `-Xlint:all,-processing -Werror`;
- 35 self-test Java sources compile with `-Werror`;
- 10 PostgreSQL test sources compile with `-Werror`;
- B1–B6 regression tests pass;
- Phase 3 Security passes;
- Phase 7 Knowledge passes;
- Phase 10 Tool Runtime passes;
- Phase 18 Foundation final verification passes;
- Phase 4 persistence regression passes;
- vendor SHA-256 verification passes;
- formatting scan passes;
- arbitrary shell/process/raw tool escape-hatch scan passes.

Environment-dependent gates remain pending rather than being reported as passed:

- canonical JDK 25 + Gradle 9.6.1 full quality build;
- PostgreSQL 18.6 live regression, including the new B6 PostgreSQL harness.

---

## 11. Next phase

```text
B7 — Self-Development Laboratory
```

B7 may now build the Self-Development lifecycle on B6 durable Tasks/Activity/Automation, B5 typed tools and the existing Foundation workspace/request/approval/runtime boundaries.
