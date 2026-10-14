# Buggy G1.4 — Native Core Foundation — Implementation Report

Date: 2026-10-06
Product source: `Legit Assistant 0.1.0-dev.19`
Foundation compatibility baseline: `0.1.0-dev.18` / Phase 0–18 accepted
Status: **IMPLEMENTED / SOURCE-VERIFIED / NOT LIVE DEPLOYED**

## 1. Purpose

G1.4 changes the development direction of Buggy from an inference-dependent assistant toward a native Legit-owned assistant core. External models are tools used later through a Model Gateway; they are not Buggy's identity, memory, authority or base runtime.

The G1.4 source deliberately does **not** configure DeepSeek, an API key, public-presence modes, advanced tools or autonomous write actions.

## 2. Protected architectural decisions

The Native Core is built around four separate security dimensions:

```text
Conversation Access
!= Information Visibility
!= Tool Permission
!= Authority
```

NetworkCore remains the authority. No maturity score, personality state, learned rule or model result may create authority.

## 3. Native-Core contracts implemented

### Birth record

- concept date is protected as `2026-10-06`;
- actual `bornAt` is created only on the first successful Native-Core application activation;
- initial maturity stage is always `BOOTSTRAP`;
- birth/baseline persistence is immutable after initialization.

The current source package therefore contains the birth mechanism but **does not claim that Buggy has already been born**. That only happens when this candidate is intentionally deployed and starts successfully.

### Maturity stages

```text
BOOTSTRAP
CHILD
APPRENTICE
YOUNG
MATURE
```

Time and message count alone cannot advance maturity.

### Maturity dimensions

- TECHNICAL
- SOCIAL
- MEMORY
- REASONING
- SELF_REFLECTION
- SECURITY_RELIABILITY
- OWNER_RELATIONSHIP

G1.4 persists only the immutable BOOTSTRAP baseline. Evolution/revisions are intentionally deferred to G1.9.

### Native personality baseline

Baby-Buggy starts with bounded numeric axes:

| Axis | Start | Minimum | Maximum |
| --- | ---: | ---: | ---: |
| Curiosity | 85 | 55 | 95 |
| Friendliness | 75 | 50 | 90 |
| Humor | 30 | 15 | 70 |
| Playfulness | 25 | 10 | 65 |
| Confidence | 30 | 15 | 90 |
| Initiative | 25 | 10 | 85 |
| Talkativeness | 45 | 20 | 80 |
| Directness | 70 | 45 | 95 |
| Technical precision | 80 | 70 | 100 |
| Social confidence | 20 | 10 | 85 |
| Reflection | 30 | 20 | 100 |
| Patience | 70 | 55 | 95 |
| Caution | 85 | 60 | 95 |

This numeric baseline is additive to the existing B2 append-only Personality system. G1.4 does not rewrite B2 history.

### Core Protocol Catalog

The following immutable protocol contracts are persisted and hash-bound:

1. Identity
2. Authority
3. Truth
4. Memory
5. Learning
6. Reflection
7. Personality
8. Relationship
9. Presence
10. Participation
11. Observation
12. Self-Improvement
13. History
14. Maturity

They constrain future learning. Learning does not rewrite these protocols.

### Initial self-capability state

G1.4 explicitly represents what Native Buggy can and cannot yet do. Examples:

- self identity: AVAILABLE;
- authority respect: AVAILABLE;
- native conversation: LIMITED until G1.6;
- native network knowledge: LIMITED until G1.7;
- long-term Native Memory: UNAVAILABLE until G1.5;
- external model: UNAVAILABLE until G1.10;
- source analysis: UNAVAILABLE until G1.11+.

This is intentional self-knowledge, not authority.

## 4. Persistence

Migration added:

`V100_006__create_buggy_native_core_foundation.sql`

It creates immutable baseline tables for:

- Native-Core seed / Birth Record;
- Core Protocol Catalog;
- numeric Personality baseline;
- Maturity baseline;
- initial self-capabilities.

All G1.4 baseline tables reject UPDATE and DELETE through PostgreSQL triggers. Future evolution must use new append-only revision structures rather than rewriting the birth baseline.

## 5. Runtime bootstrap

`G12RuntimeConfiguration` now creates `BuggyNativeCoreService` and initializes the Native Core only after the protected Buggy identity has been loaded or provisioned.

On the first successful start of this candidate:

```text
persistent Buggy identity
        -> verified/provisioned
        -> Native-Core baseline absent?
        -> initializeFirstActivation(clock.instant())
        -> Birth Record becomes persistent
```

Subsequent starts load the exact same hash-bound baseline. A different birth state is rejected instead of silently replacing history.

## 6. Compatibility

The existing B1 Identity Core is intentionally not rewritten. It is already persistent and immutable on live First-Live installations.

The existing B2 Personality system is intentionally not rewritten. The G1.4 numeric Baby-Buggy baseline is a Native-Core foundation for later bounded development and coexists with the previous append-only personality contracts.

Existing NetworkCore Authority, Panel, Minecraft and Discord transport boundaries remain unchanged.

## 7. Verification

Source verification completed in the artifact workspace:

```text
G14_DEV19_FULL_MAIN_COMPILE=PASS
main_sources=668

G14_DEV19_FULL_SELFTEST_COMPILE=PASS
selftest_sources=50

G1.4 BUGGY NATIVE CORE FOUNDATION: PASS
B12_BUGGY_ACCEPTANCE_HARDENING_SELF_TEST: PASS
B12_BUGGY_REGRESSION_CHAIN: PASS
```

B12 was explicitly updated to allow exactly `V100_006`; unexpected additional V100 migrations remain rejected.

Compilation was performed with the locally available Java 21 compiler using `--release 21`, all vendored project dependencies and the project's `-Xlint:all,-processing -Werror` policy. This verifies Java source compatibility and warning-free compilation. The canonical Java 25 / Gradle 9.6.1 build must still run before deployment because the current artifact runner cannot directly materialize the >100 MiB supplied toolchain archives.

PostgreSQL-18 execution of V100_006 is also a pre-deployment gate; no live DB was modified during this implementation pass.

## 8. Security outcome

G1.4 adds no write tool, shell execution, deployment capability, authority bypass, self-approval path or model provider.

DeepSeek/API configuration: **NOT PRESENT / NOT REQUIRED**.

## 9. Next block

`G1.5 — Memory & Development`

Planned scope:

- Session Memory;
- Experience Memory;
- Knowledge Memory;
- Relationship Memory;
- deep Primary-Owner Memory;
- provenance (`EXPLICIT`, `OBSERVED`, `INFERRED`, etc.);
- retention classes;
- memory candidates;
- Reflection records;
- Learning candidates;
- append-only Development Journal foundation.


## 10. Verification follow-up in dev.20 / G1.5 workspace

The previously pending external gates were closed on 2026-10-06 using the supplied canonical toolchain and a fresh local Percona PostgreSQL 18.6.1 instance.

```text
OpenJDK 25.0.4+7
Gradle 9.6.1
G1.4 BUGGY NATIVE CORE POSTGRES SELF TEST: PASS
G1.4 BUGGY NATIVE CORE FOUNDATION: PASS
gradle --no-daemon clean build qualityCheck: PASS
```

Direct UPDATE/DELETE attempts against the G1.4 birth seed, protocol catalog, personality baseline, maturity baseline and self-capability baseline were rejected by PostgreSQL as designed. The G1.4 source remains not live-deployed; this follow-up verifies the baseline used by the G1.5 dev.20 source candidate.
