# B8 — Buggy Control Center Backend — Implementation Report

Date: 2026-10-05
Foundation baseline: `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
Buggy specification: `BUGGYBOT-SPEC-V1` COMPLETE / APPROVED

## 1. Result

B8 implements the Owner-facing Buggy Control Center backend as a read-only projection layer over the canonical B1–B7 domains and existing Foundation-owned read models. It does not introduce a new write store, security authority, approval authority, audit store, incident authority, scheduler, tool runtime, or Panel trust boundary.

The B8 HTTP namespace is:

```text
/api/assistant/v1/buggy/...
```

All endpoints are protected by the existing Phase-15 Panel Gateway verification and every Control Center surface performs a server-side Foundation authorization check. Browser-supplied roles, permissions or capabilities are never trusted.

## 2. Exposed read surfaces

B8 exposes exactly thirteen secured Control Center surfaces:

```text
GET /api/assistant/v1/buggy/overview
GET /api/assistant/v1/buggy/identity
GET /api/assistant/v1/buggy/personality
GET /api/assistant/v1/buggy/tasks
GET /api/assistant/v1/buggy/activity
GET /api/assistant/v1/buggy/approvals
GET /api/assistant/v1/buggy/incidents
GET /api/assistant/v1/buggy/knowledge
GET /api/assistant/v1/buggy/self-development
GET /api/assistant/v1/buggy/automations
GET /api/assistant/v1/buggy/tools
GET /api/assistant/v1/buggy/security
GET /api/assistant/v1/buggy/audit
```

A pinned OpenAPI 3.1 document is shipped at `openapi/buggy-control-center-v1.yaml`. It contains no POST operation. Unsafe changes remain on the existing Phase-13 pipeline-backed Assistant API with its established idempotency, authorization, approval, audit and response-security rules.

## 3. Read-model architecture

`BuggyControlCenterReadService` projects canonical Buggy-owned state only:

- B1 Identity Core, Owner binding and active configuration;
- B2 Personality seed/current revision/history counters;
- B6 Tasks, Activity and Automations;
- B5 + B7 typed Tool catalog state;
- B7 Self-Development state and evidence summaries;
- Foundation Security Control Plane state.

Foundation-owned cross-domain data is not copied into Buggy tables. `BuggyControlCenterExternalReadPort` is the explicit read-through boundary for:

- approvals;
- incident references/projections;
- Knowledge search/results;
- immutable Audit search;
- Foundation/runtime summary data for Overview.

If the required authoritative read/security dependencies are not wired, `BuggyControlCenterConfiguration` installs a fail-closed backend that returns service-unavailable rather than fabricating or bypassing data.

## 4. Authorization model

`BuggyControlCenterSurface` maps every surface to a canonical B3 capability on the server. `SecuredBuggyControlCenterBackend` then creates an `AuthorizationRequest` using:

- the verified Phase-15 Panel service principal;
- the verified presented human actor;
- Buggy's persistent B1 `AssistantDescriptor`;
- the server-selected B3 capability;
- a restricted `buggy-control-center/<surface>` resource reference.

Authorization therefore continues to resolve through Foundation `AssistantAuthorizationService`, NetworkCore-backed permissions, resource authorization, emergency modes and kill switches. The browser cannot submit or override the effective capability.

The Security projection explicitly states:

```text
NetworkCore authoritative. Buggy cannot autonomously modify this boundary.
```

It also exposes the active capability profile, Foundation operational mode, enabled security features, active kill switches, control revision, and explicit `false` values for self-approval, self-escalation and risk downgrade.

## 5. Overview projection

The Overview projects a bounded Owner summary including:

- Buggy identity/configuration/personality revisions;
- current work summary and recent Activity count;
- active/waiting Task counts;
- pending Owner-decision count;
- failed Task count;
- enabled Automation count;
- recent autonomous-action/failure counts;
- Self-Development proposals waiting for Owner approval;
- Foundation security authority and current operating mode;
- capability/runtime profile IDs;
- Foundation-owned runtime/health information through the external summary read-through.

The local `healthy` value means the Buggy projection successfully resolved its required canonical state; infrastructure/runtime health remains Foundation-owned and is namespaced into the Overview as `foundation.*` attributes.

## 6. Activity versus Audit

B8 preserves the specification boundary:

- Activity is a readable Buggy work history projected from B6.
- Audit is the immutable Foundation security/compliance trail and is only exposed through the authoritative external read-through.

B8 has no Audit mutation contract.

## 7. Realtime events

B8 reuses the existing bounded Phase-15 `PanelRealtimeHub` and SSE endpoint. The following sanitized product notification types were added:

```text
BUGGY_IDENTITY_CHANGED
BUGGY_PERSONALITY_CHANGED
BUGGY_TASK_CHANGED
BUGGY_ACTIVITY_RECORDED
BUGGY_AUTOMATION_CHANGED
BUGGY_SELF_DEVELOPMENT_CHANGED
BUGGY_OWNER_DECISION_REQUIRED
```

`BuggyControlCenterEventPublisher` accepts only `BUGGY_*` event types and delegates payload bounding/sanitized event constraints to the existing Panel realtime infrastructure. Full state remains retrievable through authorized API reads; SSE is not an authority or state store.

## 8. Persistence

B8 introduces **no database migration** and **no B8 write tables**.

To support bounded projections, existing Buggy repository contracts gained additive current-list reads and the existing JDBC adapters implement those reads:

- `BuggyTaskRepository.listCurrentTasks(int)`;
- `BuggyAutomationRepository.listCurrentAutomations(int)`;
- `BuggySelfDevelopmentRepository.listCurrentItems(int)`.

They read the existing B6/B7 head+revision structures. No historical row or authority record is rewritten by B8.

## 9. B9 contract

B9 may consume these read models and the existing Panel SSE stream. It must not:

- access Assistant/PostgreSQL tables directly;
- trust permissions supplied by the browser;
- mutate Identity Core;
- edit Audit;
- directly execute emergency/security changes as Buggy;
- invent POST endpoints that bypass Phase-13/Phase-15 security and approval contracts.

Owner emergency controls remain visibly separate from Buggy autonomous controls and continue to be Foundation-owned.

## 10. Verification

The B8 source gate covers:

- whole-source `javac -Xlint:all,-processing -Werror`;
- B8 domain projection self-test;
- B8 API/OpenAPI/realtime boundary self-test;
- B1–B7 Buggy regression;
- Foundation Phase 3 Security regression;
- Phase 4 Persistence regression;
- Phase 7 Knowledge regression;
- Phase 10 Tool Runtime regression;
- Phase 18 Foundation Final Verification;
- all PostgreSQL test-source compilation;
- formatting/static escape-hatch scans;
- vendored dependency SHA-256 verification.

The canonical JDK 25 + Gradle 9.6.1 full build remains `PENDING_TOOLCHAIN` in this environment because the local runtime is JDK 21 and the pinned Gradle distribution is not cached/reachable. Live PostgreSQL 18.6 gates inherited from B1/B2/B4/B6/B7 remain `PENDING_DATABASE_RUNTIME`; B8 itself adds no database schema or live-database test requirement.

## 11. Gate status

```text
B8_BUGGY_CONTROL_CENTER_BACKEND = IMPLEMENTED_SOURCE_VERIFIED
NEXT = B9_BUGGY_CONTROL_CENTER_FRONTEND
BYTE_IMPLEMENTATION = NOT_STARTED
KAESE_IMPLEMENTATION = NOT_STARTED
```
