# Legit Assistant Platform

Foundation source for the common platform behind KAESE, BYTE, and BUGGY.

## Current product source candidate

- Product source version: `0.1.0-dev.21`
- Currently running First-Live version: `0.1.0-dev.18-first-live`
- Foundation compatibility baseline: `0.1.0-dev.18` / Phase 0–18 COMPLETE / ACCEPTED
- G1.2 Runtime Completion: IMPLEMENTED / OFFLINE-VERIFIED
- G1.3 Discord: IMPLEMENTED / OFFLINE-VERIFIED
- G1.4 Native Core Foundation: IMPLEMENTED / SOURCE-VERIFIED / POSTGRESQL-18.6-VERIFIED / NOT LIVE
- G1.5 Memory & Development: IMPLEMENTED / SOURCE-VERIFIED / POSTGRESQL-18.6-VERIFIED / NOT LIVE
- G1.6 Natural Conversation: IMPLEMENTED / SOURCE-VERIFIED / NOT LIVE
- G1.7 Native Knowledge & Read-Only Tools: NEXT
- First-Live Stage 1: ACCEPTED on `services-01` with Java 25.0.4.1, Gradle 9.6.1, PostgreSQL 18 and Docker
- First-Live Stage 2: PENDING NetworkCore Authority connection
- External model activation: DEFERRED TO G1.10
- Panel / Minecraft / Discord E2E: PENDING

## Current Buggy product track

- Foundation compatibility baseline: `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
- BUGGYBOT-SPEC-V1: COMPLETE / APPROVED
- B0 — Buggy Implementation Architecture: COMPLETE
- B1 — Buggy Identity Runtime: IMPLEMENTED / SOURCE-VERIFIED
- B2 — Personality Evolution: IMPLEMENTED / SOURCE-VERIFIED
- B3 — Buggy Authority & Capability Bindings: IMPLEMENTED / SOURCE-VERIFIED
- B4 — Knowledge and Decision Memory: IMPLEMENTED / SOURCE-VERIFIED
- B5 — Typed Tool Catalog: IMPLEMENTED / SOURCE-VERIFIED
- B6 — Tasks / Activity / proactive automation: IMPLEMENTED / SOURCE-VERIFIED
- B7 — Self-Development Laboratory: IMPLEMENTED / SOURCE-VERIFIED
- B8 — Buggy Control Center Backend: IMPLEMENTED / SOURCE-VERIFIED
- B9 — Buggy Control Center Frontend: IMPLEMENTED / SOURCE-VERIFIED
- B10 — Exclusive Buggy Features: IMPLEMENTED / SOURCE-VERIFIED
- B11 — Future-assistant supervisor readiness: IMPLEMENTED / SOURCE-VERIFIED
- B12 — Buggy acceptance / hardening: IMPLEMENTED / SOURCE-VERIFIED
- Buggy V1 implementation: COMPLETE / SOURCE-VERIFIED
- Production-runtime acceptance: STAGE 1 ACCEPTED; NetworkCore Authority + Inference + channel E2E gates remain
- BYTE implementation: NOT STARTED
- KAESE implementation: NOT STARTED
- Next B-phase: NONE

Canonical Buggy implementation documents:

- `docs/buggy/BUGGYBOT-IMPLEMENTATION-ARCHITECTURE-B0-2026-10-04.md`
- `docs/buggy/B1-BUGGY-IDENTITY-RUNTIME-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B2-BUGGY-PERSONALITY-EVOLUTION-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B3-BUGGY-AUTHORITY-CAPABILITY-BINDINGS-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B3-BUGGY-NETWORKCORE-PERMISSIONS-V1.txt`
- `docs/buggy/B4-BUGGY-KNOWLEDGE-DECISION-MEMORY-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B5-BUGGY-TYPED-TOOL-CATALOG-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B5-BUGGY-TYPED-TOOL-CATALOG-V1.md`
- `docs/buggy/B6-BUGGY-TASKS-ACTIVITY-PROACTIVE-AUTOMATION-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B6-CHANGE-MANIFEST.md`
- `docs/buggy/B7-BUGGY-SELF-DEVELOPMENT-LABORATORY-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B7-CHANGE-MANIFEST.md`
- `docs/buggy/B8-BUGGY-CONTROL-CENTER-BACKEND-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B8-CHANGE-MANIFEST.md`
- `docs/buggy/B9-BUGGY-CONTROL-CENTER-FRONTEND-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B9-CHANGE-MANIFEST.md`
- `docs/buggy/B10-BUGGY-EXCLUSIVE-FEATURES-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B10-CHANGE-MANIFEST.md`
- `docs/buggy/B11-BUGGY-FUTURE-ASSISTANT-SUPERVISOR-READINESS-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B11-CHANGE-MANIFEST.md`
- `docs/buggy/B12-BUGGY-ACCEPTANCE-HARDENING-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B12-BUGGY-V1-ACCEPTANCE-MATRIX.md`
- `docs/buggy/B12-CHANGE-MANIFEST.md`
- `docs/buggy/BUGGY-NATIVE-CORE-8-BLOCK-ROADMAP-2026-10-06.md`
- `docs/buggy/G1-4-NATIVE-CORE-FOUNDATION-IMPLEMENTATION-REPORT-2026-10-06.md`
- `docs/buggy/G1-5-MEMORY-DEVELOPMENT-IMPLEMENTATION-REPORT-2026-10-06.md`
- `docs/buggy/G1-5-CHANGE-MANIFEST.md`
- `docs/buggy/G1-5-VERIFICATION-2026-10-06.txt`
- `docs/buggy/G1-6-NATURAL-CONVERSATION-IMPLEMENTATION-REPORT-2026-10-06.md`
- `docs/buggy/G1-6-CHANGE-MANIFEST.md`
- `docs/buggy/G1-6-VERIFICATION-2026-10-06.txt`

The Buggy track extends the accepted Foundation. It does not introduce a Foundation Phase 19 and must not duplicate NetworkCore security, persistence infrastructure, Knowledge, approvals, Tool Runtime, Supervisor, Pipeline, API, or Panel trust boundaries.

## Buggy B3 authority/capability binding

B3 defines the canonical `buggy-capabilities-v1` profile with all 20 BUGGYBOT-SPEC-V1 capability groups and 39 atomic `assistant.buggy.*` capabilities. Every atomic capability resolves through the existing `AssistantSecurityCatalog` to a `networkcore.assistant.buggy.*` permission and remains additionally gated by `networkcore.assistant.use.buggy`, resource authorization, emergency modes, feature controls, kill switches, and later Tool Runtime risk/approval policy. Owner-only authority mutations are intentionally not represented as direct Buggy capabilities. Privileged domains expose request-only bindings instead.

## Buggy B4 Knowledge and Decision Memory

B4 binds `buggy-knowledge-v1` to the existing Foundation Knowledge catalog across every accepted Foundation Knowledge domain. Buggy adds a broad product topic taxonomy, Owner Knowledge, authoritative Owner Decision Memory, provenance/confidence metadata, deterministic conflict detection, explicit `NO_RELIABLE_RESULT`, secret exclusion via the Foundation `SecretGuard`, and an append-only stale/dispute annotation layer that references `knowledge.documents` without duplicating Knowledge content. Owner Knowledge and Decision Memory are retrievable only in the NetworkCore-bound Owner context, and Owner decisions remain DRAFT until the existing Foundation publication workflow publishes them.

## Buggy B5 Typed Tool Catalog

B5 defines the canonical `buggy-tools-v1` catalog with 146 explicit tools across Diagnostics, Source, Build, Deployment, Infrastructure, Database, Network, Security Read, Knowledge, Bugs, Requests, Supervisor, Minecraft, Discord, Web, Mail and Panel. Every tool reuses the Phase-10 `ToolDescriptor`/`ToolRegistry`, binds to a B3 `assistant.buggy.*` capability, has strict schemas/resource types/timeouts/concurrency/classification/audit metadata, and requires an explicit trusted handler. Production deploy/rollback, database backup, Knowledge publish and mail send are request-only. Web tools require a bounded SSRF-safe handler contract, readonly SQL tools require a dedicated readonly adapter, and no arbitrary shell/SSH/Docker/write-SQL execution surface exists. Self-Development tooling remains deferred to B7.

## Buggy B6 Tasks / Activity / proactive automation

B6 adds durable revisioned Tasks, Owner-readable Activity and proactive automation orchestration with the canonical `OBSERVE → ANALYZE → PREPARE → ACT / ESCALATE` lifecycle. It reuses Foundation rate limits, self-healing budgets/cooldowns, Requests/Approvals and Audit; `ACT` is restricted to B5 `LOW + AUTONOMOUS_LOW` tools, privileged automation activation requires an approved Foundation request, and schedule definitions are persisted/evaluated without introducing a competing scheduler thread or runtime authority.

## Buggy B7 Self-Development Laboratory

B7 implements the controlled `Observe → Proposal → Workspace → Patch → Build → Test → Benchmark → Security → Self-Review → Reject/Submit → Approval → Deploy → Verify/Rollback` lifecycle. Source Registry remains authoritative, workspaces/artifacts are reference-only, all self-authored changes are at least MEDIUM risk, promotion/rollback are cryptographically bound to exact Foundation Request payloads, and no self-approval, risk downgrade, generic shell/terminal/raw execution or direct production authority is introduced.


## Buggy B8 Control Center Backend

B8 adds a read-only Owner Control Center projection under `/api/assistant/v1/buggy/...` with thirteen secured surfaces for Overview, Identity, Personality, Tasks, Activity, Approvals, Incidents, Knowledge, Self-Development, Automations, Tools, Security and Audit. It reuses the Phase-15 signed Panel trust boundary, Foundation `AssistantAuthorizationService`, B1-B7 canonical state and the existing bounded realtime hub. Cross-domain approvals/incidents/Knowledge/Audit are read through authoritative Foundation ports; B8 introduces no write store or migration and no Control Center POST bypass.

## Buggy B9 Control Center Frontend

B9 adds the Owner-facing Command / Observability / Evolution workspace as a framework-free browser bundle packaged by `assistant-panel`. It never calls the Assistant API directly: the browser consumes only a trusted Panel Backend bridge, and missing bridge wiring fails closed. The UI includes context-aware Chat handoff, evidence-first approvals, read-only Audit and Identity Core presentation, realtime refresh through the existing sanitized Panel contract, and a visually separated Foundation emergency-control rail. Emergency actions are only logical Owner intents and must be server-side authorized and routed through the existing pipeline; the frontend cannot directly mutate NetworkCore/Foundation authority.

## Buggy B10 Exclusive Features

B10 implements the remaining Buggy-exclusive V1 orchestration features: Owner Priority Channel, Network-Wide Context Switching, Cross-System Correlation, Owner Briefing, Architecture Guardian, Incident Commander, Historical Comparison, Challenge Me, Deep Investigation Mode and a derived Personal Development Journal. B10 introduces no new permission/capability namespace or persistence migration. Evidence sources map to existing B3 read capabilities, all executable actions remain behind B5/Foundation authorization and approval, and the journal is derived from existing B2/B6/B7 history rather than creating another memory store.

## Buggy B11 Future-assistant supervisor readiness

B11 prepares the future Buggy→BYTE/KAESE Supervisor relationship without starting either assistant. The B11 contract preserves target-owned Identity and Personality, NetworkCore-owned permission/security authority, target-side reauthorization of delegated work, and the target's right to reject or disagree. Actual health reads reuse the existing Foundation Supervisor service through a B3-aware authorization adapter; pause/resume/restart/degrade plans map only to existing B5 typed tools and cannot execute directly. BYTE and KAESE remain `NOT_STARTED`, no new migration is added, and no Foundation module is modified.

## Buggy B12 Acceptance / Hardening

B12 is the final Buggy V1 implementation gate. It introduces no new authority, runtime, persistence or API surface. The acceptance suite re-verifies the canonical Definition of Done, fail-closed NetworkCore/security behavior, ALL_TOOLS_OFF / READ_ONLY / MAINTENANCE precedence, the no-arbitrary-shell boundary, the B9 browser trust boundary, the reviewed Buggy migration boundary (V100_001..V100_007 after G1.4/G1.5), a deterministic 160,000-operation concurrent Buggy read-load probe, the Phase-16 security/load/memory suites, Phase-17 deployment/backup/restore/rollback contracts and Phase-18 Foundation final verification. Buggy V1 remains implementation-complete and source/offline-acceptance verified. For the dev.20/G1.5 source candidate, the canonical Java 25 / Gradle 9.6.1 full quality build and PostgreSQL 18.6 persistence regressions are now PASS. Live Panel/Minecraft/Discord acceptance and an explicit deployment gate remain separate production-runtime work.

## Current foundation state

- Phase 0: specification finalized
- Phase 1: project bootstrap complete
- Phase 2: framework-free core domain complete
- Phase 3: security foundation complete
- Phase 4: PostgreSQL persistence foundation complete
- Phase 5: persistent conversation foundation complete
- Phase 6: audit & observability foundation complete
- Phase 7: Knowledge Foundation complete
- Phase 8: Bug Foundation complete
- Phase 9: Request / Approval Foundation complete
- Phase 10: Tool Runtime complete
- Phase 11: Supervisor & Self-Healing Foundation complete
- Phase 12: Integration Contracts complete
- Phase 13: Runtime Pipeline complete
- Phase 14: Assistant API complete
- Phase 15: Panel Integration complete
- Phase 16: Hardening & Performance complete
- Phase 17: Deployment & Operations complete
- Phase 18: Foundation Final Verification complete

## Foundation final verification

Phase 18 adds no new product authority or business behavior. It provides the final cross-foundation acceptance module, re-runs the complete Phase 0–17 gate stack, and explicitly verifies deployment, security authority, emergency modes, data/security controls, runtime limits, panel integration, load/security/failure tests, and backup/restore readiness.

## Toolchain

- Java 25
- Gradle 9.6.1 wrapper
- Spring Boot 4.1.1 host runtime
- PostgreSQL-first JDBC persistence
- pgJDBC 42.7.13 pinned runtime driver
- Compiler static analysis: `-Xlint:all,-processing -Werror`
- Offline architecture/bootstrap/security/conversation/knowledge/bug/request self-tests
- Canonical whitespace formatter/checker

## Audit & observability foundation

Phase 6 adds an append-only audit port backed by the canonical LegitPlatform audit contract, sanitized structured logging, a fixed-cardinality metrics catalog, explicit dependency health, public liveness/readiness endpoints, a loopback-only management surface, and W3C/OpenTelemetry-compatible request/trace correlation. Critical audited operations fail closed when the central audit sink is unavailable; metrics/tracing degradation does not become a new security authority.

## Knowledge foundation

Phase 7 adds a central versioned Knowledge Catalog with nine canonical domains, immutable content revisions, explicit DRAFT/REVIEW/PUBLISHED/ARCHIVED workflow, published-only standard retrieval, resource-level ACL filtering, Data Classification ceilings, source attribution, bounded Full Text/Tags/Metadata search, and explicit `NO_RELIABLE_RESULT` semantics. PostgreSQL stores document heads separately from immutable revisions and enforces revision immutability with a database trigger. V1 intentionally does not implement vector search.

## Bug foundation

Phase 8 adds the central, producer-independent Bug Domain with dual public/internal IDs, immutable original reports, SHA-256 integrity, append-only complete history, canonical status/severity/priority values, directed Bug relations, incident references, component/version/build/server/environment mapping, and optimistic revision-based concurrency. PostgreSQL enforces original-report immutability and append-only history with triggers; all Bug-head mutations and their history entry commit in one transaction.

## Request / Approval foundation

Phase 9 adds a generic Request aggregate with PENDING/APPROVED/DENIED/EXPIRED/CANCELLED/EXECUTING/EXECUTED/FAILED lifecycle, LOW/MEDIUM/HIGH/CRITICAL risk policy, multi-approval thresholds, mandatory CRITICAL expiry, HIGH/CRITICAL self-approval denial by default, and exact payload SHA-256 binding. Payload revisions, approval decisions, and request history are append-only. Approvals are additionally bound to a monotonic payload revision, so changing a payload away and later back to byte-identical content cannot replay an old approval. PostgreSQL enforces immutable creation policy/provenance, append-only child records, exact payload hash/revision foreign-key binding, and optimistic head revisions. HIGH/CRITICAL mutations pass the existing central Audit contract before state mutation and fail closed when that audit boundary is unavailable.

## Tool Runtime foundation

Phase 10 adds a framework-free controlled Tool Runtime with explicit `ToolDescriptor` registration, canonical capability binding, Phase-3 authorization delegation, strict flat input/output schemas, classification ceilings, risk policy, Phase-9 exact approval binding for HIGH/CRITICAL tools, per-tool concurrency semaphores, bounded execution timeouts, mandatory secret-redacting output filtering, tool metrics, and central audit reuse. The runtime exposes no arbitrary command or shell execution contract; tools are trusted typed handlers registered in-process. Descriptor registration rejects capability-risk downgrades and restricts automatic self-healing eligibility to the canonical `assistant.selfheal.low` capability.

## Conversation foundation

Phase 5 provides:

- immutable conversation/message domain models
- GENERAL, BUG_INTAKE, BUG_ANALYSIS, KNOWLEDGE, DIAGNOSTICS, REQUEST contexts
- explicit lifecycle state machine with terminal-state protection
- 32 KiB single-message limit
- bounded per-conversation message count and total persisted context bytes
- bounded metadata, attachment-count and retrieval-context contracts
- context-sensitive inactivity expiry policy and bounded expiry sweep hook
- optimistic conversation revisions
- transactional message sequence allocation
- PostgreSQL conversation/message schema and indexes
- JDBC repository adapter isolated in `assistant-integrations`
- live PostgreSQL concurrency verification

The domain module does not import Spring, JDBC, persistence infrastructure, NetworkCore adapters, or API adapters.

## Commands

```bash
./gradlew clean build
./gradlew qualityCheck
./gradlew :assistant-buggy:selfTest
./gradlew :assistant-buggy:personalitySelfTest
./gradlew :assistant-buggy:knowledgeSelfTest
./gradlew :assistant-buggy:toolCatalogSelfTest
./gradlew :assistant-buggy:automationSelfTest
./gradlew :assistant-buggy:controlCenterSelfTest
./gradlew :assistant-buggy:exclusiveFeaturesSelfTest
./gradlew :assistant-buggy:supervisorReadinessSelfTest
./gradlew :assistant-buggy:acceptanceHardeningSelfTest
./gradlew :assistant-api:buggyControlCenterSelfTest
./gradlew :assistant-panel:buggyControlCenterFrontendSelfTest
./gradlew :assistant-security:buggyAuthoritySelfTest
./gradlew :assistant-integrations:buggyAuthorityBindingSelfTest
./gradlew :assistant-integrations:buggyPersonalitySecretGuardSelfTest
./gradlew :assistant-integrations:buggyKnowledgeSecretGuardSelfTest
./gradlew :assistant-integrations:buggyAutomationActivationGuardSelfTest
./gradlew :assistant-conversations:selfTest
./gradlew :assistant-observability:selfTest
./gradlew :assistant-knowledge:selfTest
./gradlew :assistant-bugs:selfTest
./gradlew :assistant-requests:selfTest
./gradlew :assistant-runtime:selfTest
./gradlew :assistant-pipeline:selfTest
./gradlew :assistant-supervisor:selfTest
./gradlew :assistant-security:selfTest
./gradlew :assistant-integrations:selfTest
./gradlew :assistant-bootstrap:selfTest
./gradlew :assistant-hardening:selfTest
./gradlew :assistant-operations:selfTest
./gradlew :assistant-bootstrap:installDist
./scripts/smoke-test.sh
```

With a migrated PostgreSQL test database:

```bash
export LEGIT_ASSISTANT_POSTGRES_TEST_URL='jdbc:postgresql://127.0.0.1:5432/legit_assistant?sslmode=verify-full&sslrootcert=/path/to/ca.crt'
export LEGIT_ASSISTANT_POSTGRES_TEST_USER='legit_assistant_app'
export LEGIT_ASSISTANT_POSTGRES_TEST_PASSWORD='...'
./scripts/phase9-postgres-test.sh
./scripts/buggy-b1-postgres-test.sh
./scripts/buggy-b2-postgres-test.sh
./scripts/buggy-b4-postgres-test.sh
./gradlew :assistant-integrations:buggyTasksAutomationPostgresSelfTest
```

## Phase 11 — Supervisor & Self-Healing Foundation

Phase 11 adds a security-gated read-only `AssistantSupervisorReadPort`, bounded per-assistant health telemetry, dependency/degraded-capability projections, and typed automatic Self-Healing layered strictly on the Phase-10 Tool Runtime. Automatic Self-Healing is possible only for the canonical `assistant.selfheal.low` capability with a LOW-risk, explicitly `selfHealingEligible` descriptor and fail-closed audit. Phase-3 authorization still enforces authoritative capability/permission/resource/feature/kill-switch/operational-mode policy. Sliding-window budgets, global cooldown, same-target suppression, per-target max attempts, and failure escalation prevent loops. Escalation exposes only a narrow report/incident-reference port; Phase-12 integrations are not pulled forward.

## Phase 12 — Integration Contracts

Phase 12 introduces the framework-free `assistant-integration-contracts` module. It defines immutable and bounded contracts for `SourceRegistryPort`, `DiagnosticReadPort`, `ControlledWritePort`, `InferencePort`, and `ExternalAnalysisPort` without adding productive dangerous adapters. Source access is snapshot-bound and uses traversal-safe logical paths. Diagnostics exposes only fixed read operations and has no shell/process/command escape hatch. Controlled writes are a closed typed command set and require a Phase-9 approval hash bound to the exact canonical write payload. Inference is provider-neutral and enforces classification ceilings. External analysis requires sanitized payload items, a deterministic SHA-256/size/classification manifest, and an exact Phase-9 approval hash bound to that manifest. All provided adapters are fail-safe no-ops.


## Phase 13 — Runtime Pipeline

Phase 13 adds the framework-free `assistant-pipeline` module and the canonical request execution path: Incoming Request → Authentication → Actor Resolution → ingress Authorization → Conversation Resolution → Capability Resolution → Tool/Domain Routing → Policy → Execution → Response Security → Formatting → Audit → Response. The early authorization stage checks service/assistant access against the authoritative Phase-3 security snapshot; capability/resource authorization still runs fail-closed in Policy after capability resolution. The pipeline adds cancellation with future interruption, route/request timeouts, bounded active concurrency plus a bounded queue, sliding-window rate limiting, deterministic payload-bound idempotency, dependency-scoped partial degradation, final response secret redaction/classification enforcement, request metrics, and request-level non-blocking audit. Security-critical tool/request audits remain fail-closed in their owning layers.

## Phase 14 — Assistant API

Versioned HTTP API at `/api/assistant/v1/` with cursor pagination, a unified error model, mandatory idempotency keys for unsafe POSTs, and a pinned OpenAPI 3.1 contract. Production backend wiring remains fail-closed until Phase 15.

## Phase 15 — Panel Integration

Phase 15 adds the framework-free `assistant-panel` trust boundary and reference Panel Backend client. The browser never supplies trusted permissions, ranks, capabilities, or security revisions. Panel service identity and human user provenance are carried as separate request-bound HMAC credentials; the user context is short-lived, replay-protected, and cryptographically bound to service credential JTI, request ID, and trace ID. The Assistant still resolves authorization through the existing Phase-3/NetworkCore authority. Mutating API operations delegate to the Phase-13 runtime pipeline, Panel read models use a separate secured port, degraded state is projected per surface, and sanitized realtime notifications use bounded SSE. There is no Panel database access or generic shell/process execution path.

## Phase 16 — Hardening & Performance

Phase 16 adds the verification-only, framework-free `assistant-hardening` module. It runs the complete security attack matrix for forged actor context, revoked permissions, NetworkCore outage, token replay, self-approval, expired approval, secret leakage, SSRF, path traversal, tool injection, payload abuse, rate-limit bypass, and self-healing loops. It also benchmarks the seven required internal non-AI surfaces (Conversations, Knowledge, Bugs, Authorization, Rate Limiting, Supervisor Metrics, Tool Queue) against the canonical P50/P95/P99 budgets, probes bounded queue behavior under contention, scans JDBC repository loop bodies for obvious N+1 query execution, verifies explicit bounds on replay/rate/telemetry/SSE primitives, and runs a retained-heap soak test. The module is verification-only and does not introduce a second runtime authority or production execution path.

## Phase 17 deployment and operations

Phase 17 adds a hardened deployment contract and reproducible operations tooling. The canonical container runs as numeric UID/GID 10001 on a minimal Java 25 runtime image. Kubernetes enforces non-root execution, read-only root filesystem, dropped Linux capabilities, RuntimeDefault seccomp, no service-account token, no hostPath/Docker socket, bounded `/tmp`, liveness/readiness probes, and default-deny ingress/egress with explicit Panel/DNS/PostgreSQL/NetworkCore allowlists. PostgreSQL backups cover the Assistant-owned `assistant`, `bugs`, `knowledge`, `requests`, and `audit` schemas using custom-format archives, atomic writes, SHA-256 verification, and guarded restore. The runbook fixes initial recovery targets at RPO <= 15 minutes and RTO <= 60 minutes and documents staging, graceful shutdown, restart safety, dependency outages, rollback, and secret rotation.
