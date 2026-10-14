# G0 — Buggy Runtime Acceptance Report

Date: 2026-10-05

## Status

**G0 RUNTIME ACCEPTED**

The frozen Buggy V1 B12 implementation has now passed the two external environment gates that remained open after B12:

1. the canonical Java 25 / Gradle 9.6.1 build and quality gate;
2. live PostgreSQL 18.6 migration and persistence regression testing.

This status does **not** mean that Buggy is already live-integrated with the Legit website, NetworkCore, or Discord. Those integrations belong to G1. Until G1 binds the required external dependencies, application readiness remains fail-closed by design while liveness remains healthy.

## Canonical runtime used

- Java: Eclipse Temurin OpenJDK 25.0.4+7 LTS
- Gradle: 9.6.1
- Database runtime: Percona Server for PostgreSQL 18.6.1, PostgreSQL 18.6 compatible
- PostgreSQL JDBC: 42.7.13

All components were run from the artifacts supplied for the G0 acceptance run.

## Java 25 / Gradle 9.6.1 acceptance

The complete project was built with the canonical toolchain using the offline vendor dependency set:

```text
gradle --offline --no-daemon clean build qualityCheck :assistant-bootstrap:installDist
```

Result:

```text
BUILD SUCCESSFUL
152 actionable tasks
```

The run included the Buggy B1–B12 regression chain, Foundation Phase 18 final verification, Phase 16 security/load/memory hardening, Phase 17 deployment/operations verification, panel integration, security, tool runtime, requests/approvals, knowledge, persistence, and supervisor checks.

## PostgreSQL 18.6 acceptance

A new PostgreSQL 18.6-compatible cluster was initialized from scratch. All 16 current migrations were applied in version order, including all Buggy migrations:

- `V100_001`
- `V100_002`
- `V100_003`
- `V100_004`
- `V100_005`

All 11 PostgreSQL live harnesses then passed against a freshly migrated database:

- Phase 4 persistence
- Phase 6 observability
- Phase 5 conversations
- Phase 7 knowledge
- Phase 8 bugs
- Phase 9 requests/approvals
- B1 Buggy identity
- B2 Buggy personality
- B4 Buggy knowledge
- B6 Buggy tasks/automation
- B7 Buggy self-development

## Runtime defects found and hardened during G0

The live gate found one real Buggy domain defect and two stale PostgreSQL test-harness cleanup assumptions.

### B2 persisted rollback

A persisted personality rollback can remove attributes that were introduced after the rollback target revision. The previous revision invariant incorrectly required every changed attribute to still exist in the resulting state. This prevented a valid persisted rollback.

The invariant now preserves the strict requirement for seed/evolution revisions while allowing a rollback to record removed attribute keys. A dedicated offline regression test was added and the B2 PostgreSQL live test passes with the fix.

### Phase 7 cleanup after B4

The Phase 7 PostgreSQL cleanup used a direct `TRUNCATE` of Knowledge tables. B4 introduced a legitimate foreign-key reference from Buggy knowledge annotations. The isolated test cleanup now uses `CASCADE`; production data semantics are unchanged.

### B6/B7 cleanup table name

The B6 and B7 PostgreSQL harness cleanup referenced the obsolete name `assistant.buggy_personality_evidence`. The final B2 schema uses `assistant.buggy_personality_observation_evidence`. Both harnesses now target the actual migration-defined table.

## Bootstrap and restart acceptance

The installed Spring Boot distribution was started twice using Java 25.

For both starts:

- management liveness returned HTTP 200 / `UP`;
- graceful SIGTERM shutdown succeeded;
- a subsequent restart succeeded;
- readiness returned HTTP 503 / `OUT_OF_SERVICE` because NetworkCore and Database runtime dependencies are intentionally `NOT_CONFIGURED` before G1.

That readiness result is the required fail-closed behavior and is not a G0 failure.

## PostgreSQL restart / persistence / reconnect

A persistence probe was written to the live database. PostgreSQL was then stopped with a fast controlled shutdown and verified unavailable. The same cluster was restarted.

After restart:

- the probe value was still present;
- Phase 4 PostgreSQL persistence self-test passed;
- B1 Buggy identity PostgreSQL self-test passed using a new JDBC connection.

This verifies persisted state survival plus application-side JDBC reconnect after a real database restart.

## Security / architecture result

G0 did not add a new authority, runtime subsystem, database domain, approval path, tool bypass, or assistant. BYTE and KAESE remain `NOT_STARTED`.

The runtime-discovered corrections are limited to the B2 rollback invariant and PostgreSQL test-harness cleanup compatibility.

## G0 conclusion

The two B12 external runtime gates are now closed.

```text
BUGGY_V1_IMPLEMENTATION = COMPLETE
G0_RUNTIME_ACCEPTANCE = ACCEPTED
JAVA25_GRADLE_9_6_1_GATE = PASS
POSTGRESQL_18_6_LIVE_GATE = PASS
NEXT = G1_INITIAL_LIVE_INTEGRATION
```
