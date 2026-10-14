# Legit Assistant Live Candidate — 2026-10-06

Canonical product version: `0.1.0-dev.19`.

This release candidate is based on the accepted Foundation compatibility baseline `0.1.0-dev.18` and contains:

- Buggy B1–B12;
- G1.0 shared channel layer;
- G1.1 Panel bridge;
- G1.1.5 delegated Buggy access;
- G1.2 Minecraft transport/authority and Runtime Completion;
- G1.3 Discord contracts and runtime integration;
- First-Live Docker/PostgreSQL hardening;
- cumulative First-Live Hotfixes 1–5.

## First-Live Stage 1 acceptance

Accepted on `services-01` on 2026-10-06:

- Java 25 / Gradle 9.6.1 quality gate passed;
- Docker image built successfully;
- PostgreSQL 18 healthy;
- Assistant healthy;
- 5 application schemas / 38 tables present;
- runtime DB role functional;
- persistent `BUGGY` identity present exactly once;
- PRIMARY_OWNER binding matched configuration;
- PostgreSQL container recreate preserved the exact Buggy identity fingerprint;
- `/health/live` returned HTTP 200 / UP;
- `/health/ready` returned HTTP 503 / NOT_READY as expected while NetworkCore Authority is not connected.

## Remaining acceptance gates

1. NetworkCore Authority connection and readiness transition 503 -> 200.
2. Inference provider activation and first real Buggy response.
3. Panel E2E.
4. Minecraft E2E.
5. Discord Buggy Bot E2E.
6. Triple-channel regression/negative-security acceptance.
