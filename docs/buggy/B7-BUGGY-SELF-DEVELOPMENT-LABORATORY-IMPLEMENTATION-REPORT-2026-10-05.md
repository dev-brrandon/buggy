# B7 — Buggy Self-Development Laboratory — Implementation Report

Date: 2026-10-05
Foundation baseline: `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
Buggy specification: `BUGGYBOT-SPEC-V1` COMPLETE / APPROVED

## 1. Result

B7 implements the canonical Buggy Self-Development lifecycle without creating a second Source Registry, Security authority, Request/Approval system, Tool Runtime, Audit system, deployment authority, or generic execution surface.

Canonical lifecycle:

```text
Observe → Proposal → Workspace → Patch → Build → Test → Benchmark → Security
→ Self-Review → Reject / Submit → Approval → Deploy → Verify / Rollback
```

The implementation deliberately separates **analysis and preparation** from **promotion authority**. Buggy may discover, propose, work in a controlled workspace, build, test, benchmark, run security checks, compare results, and reject its own proposal. Promotion and rollback remain bound to the existing Foundation Request/Approval lifecycle and controlled deployment policy.

## 2. Source-of-truth and workspace boundary

`SourceRegistryPort` remains the source authority. B7 persists only immutable source snapshot metadata:

- component;
- version;
- build;
- revision;
- created-at timestamp;
- SHA-256 checksum.

Normal Self-Development rows never store a second uncontrolled source copy or compiled binary. Workspace, patch, build, deployment and verification artifacts are stored as bounded references plus SHA-256 evidence.

Before promotion submission, B7 resolves the source snapshot again and requires the component/version/build/revision/checksum tuple to remain identical. A stale source snapshot fails closed with a rebase-required error.

## 3. Self-Development state model

Implemented canonical states:

```text
OBSERVED
PROPOSAL
IMPLEMENTING
TESTING
REJECTED_BY_BUGGY
WAITING_FOR_OWNER_APPROVAL
APPROVED
DEPLOYED
VERIFIED
ROLLED_BACK
```

Each transition creates a new append-only `BuggySelfDevelopmentRevision`. `assistant.buggy_selfdev_items` is only the mutable head pointer.

The state model enforces structural invariants, including:

- OBSERVED cannot already contain proposal/workspace/patch/build/approval data;
- proposal metadata and risk are mandatory after observation;
- workspace evidence is required from implementation onward;
- patch evidence is required from testing onward;
- build evidence and exact approval binding are required for promotion/deployment states;
- rollback request creation does not pretend rollback has already occurred.

## 4. No Self-Development risk discount

B7 explicitly rejects `LOW` for self-authored source/workspace proposals. Self-Development tools are registered at **MEDIUM** risk or higher policy context.

No lifecycle operation may convert a HIGH/CRITICAL effect into smaller LOW operations to evade normal deployment, approval or authority policy.

Authority/security mutation is intentionally absent from `BuggySelfDevelopmentChangeType`; B7 does not provide a path for Buggy to edit its own permissions, approval policy, risk policy, emergency controls or kill switches.

## 5. Typed Self-Development tool catalog

B7 adds the separate canonical catalog:

```text
buggy-selfdev-tools-v1
```

with eleven explicit tools:

```text
tool.selfdev.observe
tool.selfdev.create-proposal
tool.selfdev.create-workspace
tool.selfdev.modify-source
tool.selfdev.build
tool.selfdev.test
tool.selfdev.benchmark
tool.selfdev.security-scan
tool.selfdev.compare
tool.selfdev.reject-own-proposal
tool.selfdev.submit-for-approval
```

They register into the existing Foundation `ToolRegistry`. No handler is synthesized from text. Every handler must be explicitly bound.

`tool.selfdev.submit-for-approval` is `REQUEST_ONLY` and accepts only a `REQUEST_CREATOR` handler. Other B7 tools require bounded typed direct handlers. Tool resource scopes and the existing Foundation `SecretGuard` are checked before handler execution.

No shell, terminal, raw SSH, Docker exec, raw SQL write, command passthrough or universal execution tool exists.

## 6. Evidence gates

Promotion requires successful current evidence for:

- workspace creation;
- patch creation;
- build;
- tests;
- security scan;
- benchmark or impact comparison;
- self-review.

Evidence is append-only and contains only typed metadata/reference material:

- evidence ID;
- item/revision;
- evidence type;
- canonical tool ID where applicable;
- artifact/reference ID;
- SHA-256;
- success status;
- actor;
- timestamp.

A failed latest self-review allows Buggy to reject its own proposal. It does not grant any promotion authority.

## 7. Foundation Request / Approval binding

`FoundationSelfDevelopmentApprovalGateway` reuses the Phase-9 `RequestService`.

Promotion request type:

```text
BUGGY_SELF_DEVELOPMENT_PROMOTION
```

Rollback request type:

```text
BUGGY_SELF_DEVELOPMENT_ROLLBACK
```

Requests are created by the service actor `assistant:buggy`, not by impersonating the owner. Therefore owner approval remains a distinct actor decision.

The approval binding stores:

- request ID;
- exact submitted Self-Development revision;
- request type;
- exact active payload SHA-256;
- exact risk level.

APPROVED / EXECUTING / EXECUTED status is accepted only when the Foundation request still references the exact approved payload revision and SHA-256. Any subsequent payload revision invalidates the original B7 binding.

Deployment completion requires the exact promotion request to reach `EXECUTED`, not merely `APPROVED`.

Rollback follows the same model: creating a rollback request does not mutate the item to ROLLED_BACK; that state is reached only after the exact rollback request is executed.

## 8. Secret and audit boundaries

Free-form Self-Development title/problem/proposal/reason data is validated by `FoundationSecretGuardedSelfDevelopmentContentPolicy`. Detected secret material is rejected before persistence rather than redacted into a misleading source-development record.

Lifecycle mutations use the existing `AuditRecorder`. Promotion/deployment/rollback boundaries are treated conservatively and use fail-closed critical audit behavior where required.

## 9. Persistence

Migration:

```text
V100_005__create_buggy_self_development.sql
```

Adds:

- `assistant.buggy_selfdev_items`
- `assistant.buggy_selfdev_revisions`
- `assistant.buggy_selfdev_evidence`

Revision and evidence history is append-only. PostgreSQL triggers reject UPDATE/DELETE attempts on historical Self-Development revisions and evidence. The mutable item table stores only the current revision pointer and timestamps.

The database additionally enforces important state-shape constraints, including the prohibition of LOW-risk Self-Development revisions after observation.

## 10. Verification

Final local source gate:

- 558 main Java sources compile with `-Xlint:all,-processing -Werror`;
- 37 self-test Java sources compile with `-Werror`;
- 11 PostgreSQL test sources compile with `-Werror`;
- B1–B7 Buggy regression tests pass;
- B7 Foundation Request/Approval and SecretGuard binding test passes;
- Phase 3 Security passes;
- Phase 4 Persistence passes;
- Phase 7 Knowledge passes;
- Phase 10 Tool Runtime passes;
- Phase 18 Foundation Final Verification passes;
- vendor SHA-256 verification passes;
- format/whitespace gate passes;
- migration integrity gate passes;
- arbitrary process/shell escape-hatch scan passes.

Environment-dependent gates remain pending rather than being represented as successful:

- canonical JDK 25 + Gradle 9.6.1 full quality build;
- PostgreSQL 18.6 live regression, including `B7BuggySelfDevelopmentPostgresSelfTest`.

## 11. Next phase

```text
B8 — Buggy Control Center Backend
```

B8 may expose B1–B7 state through the existing API/Panel Gateway contracts, but it must not create a parallel administration authority or allow the panel to bypass Security, Request/Approval, Tool Runtime, Audit, or Self-Development promotion gates.
