# Buggy G1.5 — Memory & Development — Implementation Report

Date: 2026-10-06
Product source: `Legit Assistant 0.1.0-dev.20`
Foundation compatibility baseline: `0.1.0-dev.18` / Phase 0–18 accepted
Native-Core baseline: G1.4 / `0.1.0-dev.19`
Status: **IMPLEMENTED / SOURCE-VERIFIED / POSTGRESQL-18.6-VERIFIED / NOT LIVE DEPLOYED**

## 1. Purpose

G1.5 gives Native Buggy a bounded, provenance-aware memory and development layer without creating a second source of truth for data already owned by Foundation subsystems.

The block implements:

- Session Memory;
- Experience Memory;
- Knowledge Memory;
- Relationship Memory;
- Primary-Owner Memory;
- provenance and confidence;
- retention, pinning and append-only superseding;
- Reflection records;
- Learning Candidates;
- current-self capability revisions;
- a native Development Journal.

G1.5 deliberately does **not** implement natural conversation (G1.6), read-only network tooling (G1.7), presence/observation (G1.8), autonomous learning/personality maturity changes (G1.9), external-model routing (G1.10), or advanced/write tools (G1.11+).

## 2. Memory ownership model

Existing Foundation stores remain authoritative:

```text
Conversation Foundation -> authoritative conversation/message history
Knowledge Foundation    -> authoritative documents/knowledge state
Native Memory           -> bounded assertions + provenance + source references
```

Therefore:

- `SESSION` memories must reference `conversation:<id>`;
- `KNOWLEDGE` memories must reference `knowledge:<id>`;
- G1.5 does not copy entire conversation or Knowledge records into a competing store;
- `EXPERIENCE`, `RELATIONSHIP` and `PRIMARY_OWNER` memories are native persisted memory categories.

## 3. Information visibility and Primary Owner isolation

The existing four-dimensional security model remains mandatory:

```text
Conversation Access
!= Information Visibility
!= Tool Permission
!= Authority
```

`PRIMARY_OWNER` memory is bound to the persistent Primary Owner identity. Delegated Buggy access does not imply access to Primary-Owner memory.

Relationship memory is visible only to its subject actor and the Primary Owner through the actor-projected service API. Buggy's internal memory path remains separate from user projection.

Primary-Owner memory cannot use `SESSION` retention, including through later retention revisions.

## 4. Memory lifecycle

Memory enters persistence through explicit candidates. A candidate can be accepted or rejected. Accepted candidates create revision 1 and a current-head pointer.

Subsequent change is append-only:

```text
candidate
  -> ACCEPTED
  -> memory revision 1
  -> revision 2
  -> revision N
       + current head pointer
```

Older revisions are never rewritten. Retention changes, content superseding and retirement append new revisions.

Supported provenance:

- `EXPLICIT`
- `OBSERVED`
- `INFERRED`
- `DERIVED`
- `IMPORTED`

Supported retention:

- `SESSION`
- `SHORT_TERM`
- `LONG_TERM`
- `PERMANENT`

Pinned `SESSION` memory is forbidden. `PRIMARY_OWNER + SESSION` is forbidden both in Java domain validation and PostgreSQL constraints.

## 5. Reflection and Learning Candidates

A Reflection stores only bounded conclusions, lessons and evidence references. It does not persist hidden/private chain-of-thought.

A Learning Candidate records a proposed reusable rule derived from a persisted Reflection. In G1.5, this remains a **candidate only**.

G1.5 does not grant the Learning Candidate permission to:

- mutate personality;
- increase maturity;
- create tool authority;
- change NetworkCore permissions;
- self-approve or deploy changes.

Application of bounded learning and maturity evolution remains G1.9 scope.

## 6. Current-self capability revisions

G1.4 Birth/Native-Core capability entries are immutable historical truth. G1.5 therefore does not rewrite e.g. the original:

```text
memory.long-term = UNAVAILABLE
```

Instead it adds append-only current-self capability revisions. This allows Buggy to represent:

```text
At birth: memory.long-term = UNAVAILABLE
Current self after verified G1.5: memory.long-term = AVAILABLE
```

without falsifying its birth state.

Capability revision text and evidence references pass the same persistable-content/SecretGuard boundary as memory content.

## 7. Persistence

Migration added:

`V100_007__create_buggy_memory_development.sql`

It creates:

- `assistant.buggy_memory_candidates`
- `assistant.buggy_memory_candidate_decisions`
- `assistant.buggy_memory_revisions`
- `assistant.buggy_memory_heads`
- `assistant.buggy_reflections`
- `assistant.buggy_reflection_memory_refs`
- `assistant.buggy_learning_candidates`
- `assistant.buggy_self_capability_revisions`
- `assistant.buggy_self_capability_heads`
- `assistant.buggy_native_development_journal`
- `assistant.buggy_native_development_journal_refs`

Candidate decisions, memory revisions, reflections, learning candidates, capability revisions and journal entries are append-only. PostgreSQL triggers reject mutation/deletion of immutable history. Mutable head tables only project the current revision and cannot delete history.

## 8. Runtime wiring

G1.5 is wired into `G12RuntimeConfiguration` through:

- `BuggyMemoryContentPolicy`;
- `FoundationSecretGuardedMemoryContentPolicy`;
- `JdbcBuggyMemoryRepository`;
- `JdbcBuggyDevelopmentRepository`;
- `BuggyMemoryService`;
- `BuggyDevelopmentService`.

The Foundation `SecretGuard` remains the canonical secret boundary. G1.5 does not introduce a parallel secret detector.

## 9. Verification

Canonical toolchain used:

```text
OpenJDK 25.0.4+7
Gradle 9.6.1
pgJDBC 42.7.13
Percona Server for PostgreSQL 18.6.1
```

Full canonical build:

```text
gradle --no-daemon clean build qualityCheck
BUILD SUCCESSFUL
159 actionable tasks
```

Native self-tests:

```text
G1.4 BUGGY NATIVE CORE FOUNDATION: PASS
G1.5 BUGGY MEMORY & DEVELOPMENT: PASS
B12_BUGGY_ACCEPTANCE_HARDENING_SELF_TEST: PASS
B12_BUGGY_REGRESSION_CHAIN: PASS B1,B2,B4,B5,B6,B7,B8,B10,B11
```

A fresh PostgreSQL 18.6 database was created and all migrations were applied in canonical version order through `V100_007`. The resulting schema contained 41 `assistant.buggy_%` tables.

Live local PostgreSQL verification:

```text
G1.4 BUGGY NATIVE CORE POSTGRES SELF TEST: PASS
G1.5 BUGGY MEMORY & DEVELOPMENT POSTGRES SELF TEST: PASS
B1_BUGGY_IDENTITY_POSTGRES_SELF_TEST: PASS
B2_BUGGY_PERSONALITY_POSTGRES_SELF_TEST: PASS
B4_BUGGY_KNOWLEDGE_POSTGRES_SELF_TEST: PASS
B6_BUGGY_TASKS_AUTOMATION_POSTGRES_SELF_TEST: PASS
B7_BUGGY_SELF_DEVELOPMENT_POSTGRES_SELF_TEST: PASS
```

The G1.5 PostgreSQL test additionally verifies:

- Primary-Owner memory isolation;
- append-only memory history;
- Reflection/Learning persistence;
- current-self capability revision while preserving the G1.4 baseline;
- native Development Journal persistence;
- SecretGuard rejection;
- database-trigger rejection of direct UPDATE/DELETE against append-only history.

## 10. Security outcome

G1.5 adds no direct tool execution, no shell, no deployment capability, no self-approval, no permission mutation, no maturity elevation, no model provider and no live autonomous learning path.

The running First-Live Assistant remains `0.1.0-dev.18-first-live`. `0.1.0-dev.20` is a source candidate only until an explicit deployment gate is performed.

## 11. Next block

`G1.6 — Natural Conversation`

Planned scope remains:

- Conversation Context;
- Intent Engine;
- Entity Resolver;
- Response Planner;
- Natural Response Composer.
