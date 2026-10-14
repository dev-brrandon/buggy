# B4 — Buggy Knowledge and Decision Memory — Change Manifest

**Date:** 2026-10-04
**Baseline:** accepted B3 source checkpoint
**Foundation:** `0.1.0-dev.18` unchanged

## Product-domain additions

- canonical `buggy-knowledge-v1` profile binding;
- broad Buggy topic taxonomy mapped onto all nine accepted Foundation Knowledge domains;
- explicit `GENERAL`, `OWNER_KNOWLEDGE`, and `DECISION_MEMORY` product areas;
- product visibility model for `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `OWNER_CONFIDENTIAL`, `SECURITY_SENSITIVE`, and excluded `SECRET`;
- canonical provenance model from BUGGYBOT-SPEC-V1;
- canonical confidence model `VERIFIED/HIGH/MEDIUM/LOW`;
- Owner Decision Memory input with scope, rationale, authority and optional conflict key/value;
- deterministic conflict detection with explicit `NO_RELIABLE_RESULT`;
- correction-DRAFT creation linked to stale/disputed immutable Knowledge documents.

## Foundation Knowledge reuse

B4 does not create a second Knowledge store. Content, revisions, ACL, publication status, Full Text/Tag/Metadata search and source attribution remain owned by `assistant-knowledge` and `knowledge.*` PostgreSQL tables.

Buggy-created documents are normal Foundation `KnowledgeDocument`/`KnowledgeRevision` records. Owner decisions remain DRAFT until the existing `DRAFT -> REVIEW -> PUBLISHED` workflow publishes them.

## Owner Knowledge / Decision Memory boundary

- Owner Knowledge and Decision Memory require the B1 NetworkCore-bound Owner actor context;
- stored documents carry the existing Buggy Knowledge read ACL;
- `SECURITY_SENSITIVE` material additionally requires the existing B3 security-read permission;
- no local role, permission, or Owner identity authority was introduced.

## Stale/disputed assessment persistence

New migration:

`V100_003__create_buggy_knowledge_annotations.sql`

Tables:

- `assistant.buggy_knowledge_annotation_revisions` — append-only `ACTIVE/STALE/DISPUTED` assessments;
- `assistant.buggy_knowledge_annotation_head` — mutable pointer to the current assessment only.

The annotation layer stores no Knowledge content. Every row references `knowledge.documents(document_id)`. Revision history is immutable by database trigger.

## Secret exclusion

- normal `SECRET` classification is rejected for Buggy Knowledge;
- the existing Foundation `SecretGuard` is reused at write and retrieval boundaries;
- misclassified secret-like material is excluded from Buggy retrieval;
- secret references such as `secret_ref=legit/prod/postgres` remain permitted because they do not expose the secret value.

## Verification

Passed locally:

- complete main-source compilation with `-Werror`;
- complete self-test compilation with `-Werror`;
- complete PostgreSQL-test source compilation with `-Werror`;
- Phase-3 Security regression;
- Phase-7 Knowledge Foundation regression;
- B1 Identity regression;
- B2 Personality regression;
- B3 authority/profile regression;
- B4 Knowledge/Decision Memory self-test;
- B4 Foundation SecretGuard integration self-test;
- Phase-10 Tool Runtime regression;
- Phase-18 Foundation Final Verification regression;
- Phase-4 Persistence migration catalog regression;
- vendor integrity / format / escape-hatch checks.

Environment-dependent acceptance remains pending for the canonical Java 25/Gradle 9.6.1 gate and live PostgreSQL 18.6 execution. The B4 PostgreSQL harness is compiled and ready.

## Next

`B5 — Typed Tool Catalog`
