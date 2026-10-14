# B3 — Buggy Authority & Capability Bindings — Change Manifest

**Date:** 2026-10-04
**Baseline:** accepted B2 source checkpoint
**Foundation:** `0.1.0-dev.18` unchanged

## Security-domain additions

- canonical `buggy-capabilities-v1` profile;
- 20 BUGGYBOT-SPEC-V1 capability groups;
- 39 atomic `assistant.buggy.*` capabilities;
- READ / EXECUTE / MUTATE / REQUEST_ONLY product bindings;
- explicit OWNER_ONLY action boundary with no direct capability grant;
- NetworkCore-backed rule for every atomic capability;
- NetworkCore provisioning view through `BuggySecurityCatalog.requiredNetworkCorePermissions()`;
- `BuggyCapabilityPolicy` limited to `AssistantId.BUGGY`.

## Foundation Security integration

- B3 rules are merged into the existing `AssistantSecurityCatalog`;
- `AssistantCapabilityPolicy` now has a backwards-compatible descriptor-aware hook;
- `AssistantAuthorizationService` uses the descriptor-aware hook;
- the canonical Buggy policy requires `capabilityProfileId=buggy-capabilities-v1`;
- old static policies retain their previous semantics through the interface default method;
- accepted Foundation capabilities remain present and unchanged.

## Authority boundaries

- NetworkCore remains authoritative for actor identity and permissions;
- `networkcore.assistant.use.buggy` remains mandatory;
- every B3 capability additionally requires its own `networkcore.assistant.buggy.*` permission;
- resource authorization, operational mode, feature gates and kill switches remain Foundation-owned;
- no approval decision capability and no direct Owner-only security mutation capability is included in the Buggy profile;
- REQUEST_ONLY capabilities prepare/create requests rather than perform the target privileged operation.

## Persistence

No B3 migration.

The profile selection already lives in B1's versioned configuration. NetworkCore security state is deliberately not duplicated into Assistant persistence.

## Verification

Passed locally:

- complete main-source compilation with `-Werror`;
- complete self-test compilation with `-Werror`;
- Phase-3 Security regression;
- B1 Identity regression;
- B2 Personality regression;
- B3 capability/profile/NetworkCore/emergency-control self-test;
- B3 manifest/profile cross-module integration test;
- Phase-10 Tool Runtime regression;
- Phase-18 Foundation Final Verification regression;
- format/escape-hatch/vendor integrity checks in the B3 final gate.

Environment-dependent acceptance still pending:

- canonical JDK 25 + Gradle 9.6.1 full quality gate;
- live PostgreSQL 18.6 regressions inherited from B1/B2.

## Next

`B4 — Knowledge and Decision Memory`
