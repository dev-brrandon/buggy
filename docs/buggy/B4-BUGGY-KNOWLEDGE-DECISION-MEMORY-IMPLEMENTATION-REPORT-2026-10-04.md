# B4 — BUGGY Knowledge and Decision Memory — Implementation Report

**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Date:** 2026-10-04
**Foundation compatibility baseline:** `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
**Canonical Knowledge profile:** `buggy-knowledge-v1`

---

## 1. Scope completed

B4 implements the BUGGYBOT-SPEC-V1 Knowledge/Decision Memory layer strictly on top of the accepted Foundation Knowledge system.

Implemented:

- broad Buggy Knowledge bindings across every accepted Foundation Knowledge domain;
- 17 product topics covering architecture, NetworkCore/Platform/System, Minecraft, network/server operations, source/build history, deployments, incidents, databases, Panel/APIs, Discord/Web/Mail, security, recovery, observability, historical decisions, Self-Development, future assistants and Owner context;
- separate logical Owner Knowledge area;
- authoritative Owner Decision Memory capture;
- provenance and confidence handling;
- deterministic semantic conflict keys/values;
- explicit `NO_RELIABLE_RESULT` on insufficient evidence;
- stale/disputed assessment history;
- linked correction DRAFT creation;
- Secret exclusion on both persistence and retrieval boundaries.

No Foundation Knowledge lifecycle, publication engine, ACL engine, search engine or content store was duplicated.

---

## 2. Knowledge ownership

Authoritative Knowledge content remains:

```text
KnowledgeCatalogService
  -> KnowledgeRepository / KnowledgeSearchPort
  -> knowledge.documents
  -> knowledge.revisions
```

Buggy-created Knowledge is a normal Foundation `KnowledgeDocument` plus immutable `KnowledgeRevision`.

The only B4-specific persistence is an annotation projection for Buggy's current assessment of an existing Knowledge document:

```text
assistant.buggy_knowledge_annotation_revisions
assistant.buggy_knowledge_annotation_head
```

Those rows reference `knowledge.documents(document_id)` and never duplicate title/content/revision payloads.

---

## 3. Broad Knowledge profile

Profile ID:

```text
buggy-knowledge-v1
```

Foundation domain coverage:

```text
MODERATION
STAFF_PROCEDURES
BUG_REPORTING
SERVER_OPERATIONS
MINECRAFT_ARCHITECTURE
PANEL
DEPLOYMENTS
TROUBLESHOOTING
SECURITY
```

This preserves the frozen Phase-7 domain enum/database constraints while adding a richer Buggy product taxonomy through metadata.

Normal Buggy retrieval is capped at Foundation `RESTRICTED`. `SECRET` is never admitted as normal Knowledge.

---

## 4. Visibility mapping

BUGGYBOT-SPEC-V1 visibility is mapped onto the accepted Foundation classification ladder:

| Buggy visibility | Foundation classification | Additional rule |
|---|---|---|
| PUBLIC | PUBLIC | normal Knowledge policy |
| INTERNAL | INTERNAL | normal Knowledge policy |
| CONFIDENTIAL | CONFIDENTIAL | normal Knowledge policy |
| OWNER_CONFIDENTIAL | RESTRICTED | NetworkCore-bound Owner context required |
| SECURITY_SENSITIVE | RESTRICTED | existing Buggy Knowledge + Security Read permissions required |
| SECRET | SECRET | rejected as normal Buggy Knowledge |

No new classification authority was introduced.

---

## 5. Provenance and confidence

Canonical provenance values:

```text
OWNER_DECISION
SOURCE_CODE
CONFIGURATION
DATABASE
LOG_ANALYSIS
INCIDENT
DOCUMENTATION
ASSISTANT_INFERENCE
EXTERNAL_SOURCE
```

The provenance is persisted through the existing Foundation `KnowledgeSourceAttribution.sourceKind`.

Confidence is persisted as bounded Buggy metadata:

```text
VERIFIED
HIGH
MEDIUM
LOW
```

This keeps source attribution first-class in the Foundation revision while retaining the Buggy-specific confidence interpretation.

---

## 6. Owner Knowledge

`OWNER_KNOWLEDGE` is a logical area in the shared Knowledge catalog, not a second store.

B4 requires the active B1 Owner binding before an Owner Knowledge draft can be created or retrieved. The owner identity continues to be a persisted reference to the authoritative NetworkCore actor.

Owner Knowledge may contain non-secret:

- working preferences;
- architecture preferences;
- project priorities;
- recurring workflows;
- explicit decisions;
- communication preferences.

Personality and Knowledge remain separate systems. B2 may later consume explicit Knowledge-derived evidence, but B4 does not write Personality state.

---

## 7. Decision Memory

Owner Decision Memory uses area `DECISION_MEMORY`, provenance `OWNER_DECISION` and confidence `VERIFIED`.

Each decision carries:

- decision text;
- scope;
- rationale;
- authority;
- source reference;
- optional semantic conflict key/value.

Only the current NetworkCore-bound Owner may assert `OWNER_DECISION`.

Critically, recording an Owner decision does **not** bypass Knowledge publication. The record is created as a normal Foundation DRAFT and must continue through the existing publication contract.

---

## 8. Conflict detection

B4 introduces an explicit semantic comparison contract:

```text
buggy.conflict_key
buggy.conflict_value
```

Example:

```text
conflict_key   = economy.medium
existing value = vanilla-items
candidate      = coins
```

Conflict detection searches published Foundation Knowledge by exact conflict key and compares current values.

Possible outcomes:

```text
CONFLICT
NO_CONFLICT
NO_RELIABLE_RESULT
```

If no comparable evidence exists, B4 returns `NO_RELIABLE_RESULT`; it does not infer a nonexistent rule.

---

## 9. Stale / disputed handling

B4 deliberately does not rewrite a PUBLISHED Foundation revision merely to flag it stale.

Instead an append-only Buggy assessment is created:

```text
ACTIVE -> STALE -> DISPUTED -> ACTIVE
```

Every transition is a new immutable annotation revision carrying:

- document ID;
- contiguous annotation revision;
- previous revision;
- state;
- reason;
- evidence reference;
- confidence;
- actor;
- timestamp;
- SHA-256 integrity value.

The database rejects UPDATE/DELETE on annotation revision history.

By default Buggy search excludes stale/disputed documents. They remain available when explicitly requested for investigation/history.

---

## 10. Correction flow

A correction can be created only when the superseded document currently has a `STALE` or `DISPUTED` Buggy assessment.

The correction is a new Foundation Knowledge DRAFT containing references to:

```text
supersedes_document_id
supersedes_annotation_revision
```

The old Knowledge content remains immutable and the correction must still pass the normal Foundation publication workflow.

---

## 11. Secret exclusion

B4 reuses the existing Foundation `SecretGuard` through `FoundationSecretGuardedKnowledgeContentPolicy`.

Applied at:

- draft title/content/source/metadata ingestion;
- Decision Memory ingestion;
- annotation reason/evidence ingestion;
- retrieved Knowledge title/content/source/metadata filtering.

Therefore even a pre-existing or externally-created Knowledge document that is incorrectly classified below SECRET but contains recognized secret material is excluded from Buggy context.

Secret references remain allowed when they identify a secret without embedding its value.

---

## 12. Persistence

Migration:

```text
V100_003__create_buggy_knowledge_annotations.sql
```

The migration creates only Buggy assessment metadata. It does not add another Knowledge schema.

Concurrency is serialized per Knowledge document by PostgreSQL transaction-scoped advisory lock in `JdbcBuggyKnowledgeAnnotationRepository`. The repository enforces optimistic expected-head revision checks before appending a new assessment.

---

## 13. Foundation boundaries preserved

B4 does not add or replace:

- authentication;
- NetworkCore role/permission authority;
- capability authorization;
- Knowledge content persistence;
- Knowledge ACL policy;
- Knowledge publication workflow;
- Request/Approval;
- Tool Runtime;
- Supervisor;
- Pipeline;
- API/Panel Gateway.

B5 will expose typed Knowledge tools over these B4 services.

---

## 14. Verification

Local compiler gate:

```text
javac -encoding UTF-8 -Xlint:all,-processing -Werror
```

Passed:

- 499 main Java sources;
- 32 self-test Java sources;
- 9 PostgreSQL-test Java sources;
- Phase-3 Security self-test;
- Phase-7 Knowledge self-test;
- B1 Identity Runtime self-test;
- B2 Personality Evolution self-test;
- B3 Authority/Capability self-test;
- B3 manifest/profile integration self-test;
- B4 Knowledge/Decision Memory self-test;
- B4 Foundation SecretGuard integration self-test;
- Phase-10 Tool Runtime self-test;
- Phase-18 Foundation Final Verification;
- Phase-4 Persistence self-test;
- vendor SHA-256 verification;
- repository format scan;
- production escape-hatch scan.

Compiled but not executable in the current environment:

- B4 live PostgreSQL 18.6 regression harness.

Canonical Java 25 + Gradle 9.6.1 full `qualityCheck` also remains environment-pending because the local runtime is Java 21 and the pinned Gradle distribution is not cached.

---

## 15. B4 gate

B4 is **IMPLEMENTED / SOURCE-VERIFIED**.

Next roadmap item:

**B5 — Typed Tool Catalog**
