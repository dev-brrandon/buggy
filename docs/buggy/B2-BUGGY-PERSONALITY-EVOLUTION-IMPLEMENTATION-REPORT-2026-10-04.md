# B2 — BUGGY PERSONALITY EVOLUTION — IMPLEMENTATION REPORT

**Date:** 2026-10-04
**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Foundation compatibility:** `0.1.0-dev.18`
**Previous gates:** B0 COMPLETE, B1 IMPLEMENTED / SOURCE-VERIFIED
**Next roadmap item:** B3 — Buggy authority/capability bindings

## 1. Implemented scope

B2 implements the canonical `BUGGYBOT-SPEC-V1` Personality model without introducing a second Foundation authority or security subsystem.

Implemented:

- canonical `buggy-personality-v1` Personality Seed;
- all V1 seed characteristics from the approved spec;
- versioned complete Personality State revisions (`P-0001`, `P-0002`, ...);
- append-only behavioral observations;
- reference-only observation evidence;
- explicit revision deltas (`changedAttributes`);
- explicit revision evidence (`evidenceObservationIds`);
- per-observation and per-revision confidence in basis points;
- previous-revision chaining;
- reason/origin metadata;
- Owner preference learning boundaries;
- state-level rollback as a new append-only revision;
- history/read projection for later B8 Control Center use;
- Foundation SecretGuard reuse for all free-form Personality persistence;
- hard domain guard preventing Personality from representing authority/security mutations;
- B2 personality-profile activation through the existing versioned Buggy Bot configuration;
- normalized PostgreSQL migration and JDBC adapter;
- offline B2 self-test;
- Foundation SecretGuard integration self-test;
- opt-in live PostgreSQL B2 regression harness and runner script.

## 2. Canonical Personality Seed

The initial seed is represented as immutable typed state entries:

```text
highly competent
direct
calm under pressure
dry humor
slightly cheeky
technically precise
curious
independent-minded
willing to challenge poor technical decisions
not a sycophantic yes-man
reduce humor during incidents and security events
```

Seed profile:

```text
profile_id   = buggy-personality-v1
seed_version = 1
```

The seed has deterministic SHA-256 canonicalization and is persisted separately from later Personality revisions.

## 3. Personality State model

Personality State is not a mutable JSON/blob and is not a prompt string.

Each revision retains:

```text
revision / revisionId
previousRevision
origin
reason
confidenceBps
rollbackTargetRevision (rollback only)
changedAttributes
evidenceObservationIds
complete resulting state entries
createdBy
createdAt
SHA-256
```

This directly satisfies the approved requirement that meaningful revisions preserve revision ID, timestamp, origin/evidence, changed traits/preferences, confidence, previous revision and reason.

The complete state is persisted per revision to make recovery and deterministic historical rendering independent of recomputing a long event chain.

## 4. Personality Journal / observations

An observation stores only Personality-relevant derived information plus references to evidence:

```text
observationId
domain
attributeKey
observedValue
origin
confidenceBps
evidence[] -> typed reference IDs only
note
createdBy
createdAt
SHA-256
```

Raw conversation bodies, credentials, API tokens, private keys and equivalent secret material are not an observation evidence format.

The Personal Development Journal is the ordered read projection of:

```text
Personality observations
+
Personality revisions
```

This avoids creating a second mutable journal store that could drift away from canonical state/history.

## 5. Owner preference learning boundaries

`OWNER_PREFERENCE` may only originate from:

```text
EXPLICIT_OWNER
REPEATED_OWNER_PATTERN
OWNER_KNOWLEDGE_REFERENCE
```

Promotion defaults are intentionally conservative:

```text
general state promotion       >= 6000 bps confidence
explicit Owner preference     >= 7000 bps
Owner Knowledge reference     >= 7500 bps
repeated Owner pattern        >= 8000 bps + >= 3 distinct evidence references
```

Low-confidence observations may remain in the journal without changing active Personality State.

Owner Knowledge remains a separate Foundation Knowledge concern. B2 can reference an Owner Knowledge item by ID but does not copy or replace the Knowledge document/store. B4 will bind this to the canonical Knowledge lifecycle.

## 6. Personality vs. authority/security

The only mutable Personality domains are:

```text
COMMUNICATION_STYLE
HUMOR_STYLE
WORKFLOW_ORDERING
INTERACTION_HABIT
OWNER_PREFERENCE
PERSONALITY_TRAIT
```

There is deliberately no Personality domain for:

```text
permissions
capabilities
authority
approval policy
risk policy
kill switches
security policy
roles/scopes
credentials/secrets
```

`BuggyPersonalityBoundaryGuard` additionally rejects protected attribute namespaces and directive-like values attempting to encode permission grants, authority/capability escalation, self-approval, risk downgrade, kill-switch bypass or security-policy override.

This is independent of the real Foundation authorization boundary: even a malicious/corrupt Personality value would not grant NetworkCore authority because B2 owns no authorization path.

## 7. Secret exclusion

`assistant-buggy` remains framework/security-implementation independent and depends only on the Foundation core contracts.

For persisted free text it consumes `BuggyPersonalityContentPolicy`.

Production adapter:

```text
FoundationSecretGuardedPersonalityContentPolicy
        -> existing assistant-security SecretGuard
```

Detected secret material is rejected rather than persisted in redacted form. This keeps secrets out of normal Personality content while reusing the existing Foundation secret detector.

The JDBC repository applies the same content policy defensively before writes in addition to the product service boundary.

## 8. Rollback semantics

Rollback never points the active head backward and never edits history.

Example:

```text
P-0001 seed
P-0002 learned preference
P-0003 another evolution
P-0004 ROLLBACK target=P-0001
```

`P-0002` and `P-0003` remain immutable and visible. `P-0004` contains the restored state plus an explicit delta relative to `P-0003`.

A state-equivalent/no-op rollback is rejected.

## 9. Persistence

Migration:

```text
V100_002__create_buggy_personality_evolution.sql
```

Tables:

```text
assistant.buggy_personality_seed
assistant.buggy_personality_seed_entries
assistant.buggy_personality_observations
assistant.buggy_personality_observation_evidence
assistant.buggy_personality_revisions
assistant.buggy_personality_revision_changes
assistant.buggy_personality_revision_evidence
assistant.buggy_personality_state_entries
assistant.buggy_personality_head
```

Properties:

- all history/seed/observation/state tables are append-only at database level;
- only the small Personality head row is mutable;
- PostgreSQL transaction-scoped advisory locking serializes B2 singleton/head writes;
- head updates are expected-revision checked;
- revisions and observations carry deterministic SHA-256 integrity;
- evidence stores references only;
- Personality remains under the existing Foundation-owned `assistant.*` schema.

## 10. B1 configuration integration

B1 intentionally bootstrapped with:

```text
personality_profile_id = null
```

B2 activates:

```text
buggy-personality-v1
```

through a new append-only `buggy_config_revisions` entry and advances the existing Buggy identity head. The Identity Core and NetworkCore Owner binding are untouched.

Repeated B1 provisioning remains idempotent after B2 activation by validating the protected identity/Owner binding rather than incorrectly requiring the active versioned configuration to remain revision 1 forever.

## 11. Verification performed in this environment

Source/compiler verification uses the locally available Java 21 compiler with the same static compiler gate configured by the project:

```text
-Xlint:all,-processing -Werror
```

Compiled successfully:

```text
assistant-core
assistant-buggy main
assistant-buggy B1/B2 self-tests
assistant-security
assistant-persistence
B1/B2 Buggy JDBC adapters
B2 Foundation SecretGuard adapter/self-test
B1 PostgreSQL regression harness
B2 PostgreSQL regression harness
```

Executed successfully:

```text
B1_BUGGY_IDENTITY_RUNTIME_SELF_TEST: PASS
B2_BUGGY_PERSONALITY_EVOLUTION_SELF_TEST: PASS
B2_PERSONALITY_SECRET_GUARD_SELF_TEST: PASS
PHASE4_PERSISTENCE_SELF_TEST: PASS
```

The Gradle wrapper is correctly pinned to Gradle `9.6.1` and its SHA-256, but the distribution is not present locally and this execution environment cannot reach `services.gradle.org`. The canonical Java-25/Gradle-9.6.1 gate therefore remains pending rather than being falsely reported as passed.

The B2 live PostgreSQL regression harness is compiler-verified but PostgreSQL 18.6 is not available in this execution environment, so the live migration/repository test remains pending.

## 12. B2 exit gate

```text
[x] Personality Seed
[x] versioned complete Personality State
[x] observations / Personal Development Journal projection
[x] explicit origin/evidence/delta/confidence/reason/previous revision metadata
[x] Owner preference learning boundaries
[x] rollback/history view without history rewriting
[x] personality profile activated through versioned B1 configuration
[x] protection against authority/security mutation through Personality
[x] existing Foundation SecretGuard reused
[x] secret material excluded from normal Personality persistence
[x] PostgreSQL migration
[x] JDBC adapter
[x] offline B2 self-test
[x] B1 regression remains green
[x] Phase-4 persistence regression remains green
[x] B2 PostgreSQL regression harness compiler-verified
[ ] canonical Java-25 + Gradle-9.6.1 qualityCheck (toolchain distribution unavailable/offline)
[ ] live PostgreSQL-18.6 B2 migration/repository regression (database runtime unavailable)
```

B2 is complete at source level with environment-dependent production acceptance gates carried forward to B12.

## 13. Next roadmap item

`B3 — Buggy authority/capability bindings`

B3 must map canonical Buggy bundles onto the existing Foundation Security/NetworkCore contracts. It must not create a new security authority.
