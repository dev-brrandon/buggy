# B1 — BUGGY IDENTITY RUNTIME — IMPLEMENTATION REPORT

**Date:** 2026-10-04
**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Foundation compatibility:** `0.1.0-dev.18`
**Previous gate:** B0 COMPLETE

## Implemented

- new framework-free `assistant-buggy` module;
- canonical persistent identity `BUGGY` / `buggy` / `assistant:buggy`;
- protected Identity Core with deterministic SHA-256;
- exact canonical Identity Core principles from BUGGYBOT-SPEC-V1;
- typed Manifest schema V1;
- deterministic Manifest SHA-256 canonicalization;
- versioned Buggy configuration record;
- versioned NetworkCore-backed Owner binding;
- fail-closed unprovisioned identity behavior;
- Foundation `AssistantDescriptor` projection;
- explicit Runtime Instance model;
- explicit Session Instance model bound to a Foundation conversation UUID;
- persistence port in `assistant-buggy`;
- PostgreSQL JDBC adapter in existing `assistant-integrations`;
- transaction-scoped PostgreSQL advisory lock serializing singleton identity provisioning;
- opt-in live PostgreSQL B1 regression harness and `scripts/buggy-b1-postgres-test.sh`;
- post-Foundation migration `V100_001__create_buggy_identity_runtime.sql` in existing `assistant-persistence`;
- database-level immutability for Identity Core and append-only owner/config revision history;
- no B2 personality implementation pulled forward.

## Security properties

- NetworkCore remains security authority;
- Owner binding stores only an authoritative ActorId reference;
- no default/fabricated Owner identity;
- no self-approval toggle can be enabled in the manifest;
- no authority-self-escalation toggle can be enabled;
- no risk-downgrade toggle can be enabled;
- no security permission is granted by the local Manifest;
- no public owner-rebinding API/tool is introduced;
- no shell/SSH/docker/raw SQL write capability is introduced;
- no second audit/approval/pipeline/runtime system is introduced.

## Verification performed in this environment

The canonical Phase-18 toolchain archives are checksum-pinned by the checkpoint but the JDK-25/Gradle-9.6.1 archives themselves are not present in this execution environment. Therefore a canonical Gradle/JDK-25 full build cannot be truthfully claimed here.

Performed instead:

```text
Java compiler static gate (-Xlint:all,-processing -Werror) for:
  assistant-core
  assistant-buggy
  assistant-persistence
  JdbcBuggyIdentityRepository
  B1 self-test
  B1 PostgreSQL regression harness (compile-only in this environment)

B1_BUGGY_IDENTITY_RUNTIME_SELF_TEST: PASS
PHASE4_PERSISTENCE_SELF_TEST: PASS
```

The Java source is intentionally limited to language features accepted by the available compiler during this source verification. Canonical JDK-25/Gradle verification remains a release gate once the pinned toolchain is available.

## B1 exit gate

```text
[x] persistent Buggy identity model
[x] protected Identity Core
[x] Owner binding model
[x] versioned Bot configuration
[x] identity/runtime/session separation
[x] PostgreSQL schema/migration
[x] JDBC adapter
[x] fail-closed provisioning behavior
[x] no Foundation security/runtime duplication
[x] offline source/self-test verification
[ ] canonical Java-25 + Gradle-9.6.1 clean/quality build (toolchain archive unavailable here)
[x] live PostgreSQL-18.6 regression harness implemented and compiler-verified
[ ] live PostgreSQL-18.6 migration/repository regression executed (database runtime unavailable here)
```

B1 implementation is complete at source level. The two environment-dependent verification gates are explicitly carried forward and must be run before production acceptance/B12.

## Next roadmap item

`B2 — Personality evolution`
