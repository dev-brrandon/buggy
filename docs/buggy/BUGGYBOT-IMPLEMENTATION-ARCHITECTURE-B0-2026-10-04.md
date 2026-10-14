# BUGGYBOT — IMPLEMENTATION ARCHITECTURE B0

**Status:** CANONICAL IMPLEMENTATION BASELINE
**Date:** 2026-10-04
**Foundation compatibility baseline:** `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
**Product track:** Buggy V1
**Roadmap:** B0–B12
**Next implemented milestone:** B1 — Buggy Identity Runtime

---

## 0. Precedence and non-negotiable boundary

This architecture implements `BUGGYBOT-SPEC-V1` on top of the frozen Legit Assistant Foundation. It does not create a second foundation.

The following remain authoritative and are reused exactly as designed:

- NetworkCore: identity/security/permission authority;
- `assistant-security`: authorization, service authentication, kill switches, operational modes, secret guard;
- `assistant-persistence`: schema ownership, migrations, transactions, optimistic locking;
- `assistant-knowledge`: Knowledge lifecycle, ACL, revisions, publication;
- `assistant-requests`: Requests/Approvals, payload hash/revision binding, self-approval protection;
- `assistant-runtime`: typed Tool Runtime, risk, timeout, concurrency, output filtering, audit;
- `assistant-supervisor`: health/read model and bounded Self-Healing;
- `assistant-pipeline`: canonical request execution pipeline;
- `assistant-api`: versioned HTTP boundary;
- `assistant-panel`: Panel trust boundary, user-context integrity and SSE contracts;
- `assistant-observability`: central audit/metrics/health/tracing contracts;
- `assistant-integration-contracts`: Source, Diagnostics, Controlled Write, Inference, External Analysis ports.

No Buggy module may bypass or duplicate any of those authorities.

---

# 1. Module architecture

## 1.1 New product module

One new framework-free product module is introduced:

```text
assistant-buggy
```

Responsibilities:

```text
Buggy identity model
Buggy protected Identity Core
Buggy Bot Manifest
Buggy versioned configuration domain
Buggy Owner binding model
Buggy runtime/session projection
Buggy personality domain (B2)
Buggy product policies/orchestration (B3+)
Buggy task/activity/self-development domain types (B6/B7)
Buggy-specific read-model contracts for Control Center (B8+)
```

It must not contain:

```text
NetworkCore authorization implementation
new service authentication
new approval engine
new audit store
new generic persistence engine
new generic tool runtime
new generic pipeline
new Knowledge store
new Supervisor engine
new Panel trust implementation
raw production shell/SSH/docker/sql execution
```

## 1.2 Existing modules extended, not cloned

```text
assistant-persistence
  → Buggy SQL migrations only

assistant-integrations
  → JDBC adapters and typed external adapters implementing existing ports

assistant-security
  → B3 extension of the existing capability/security catalog only

assistant-runtime
  → B5 registration of Buggy ToolDescriptors/handlers through the existing ToolRegistry

assistant-supervisor
  → B11 target contracts/read projections only where generic support is needed

assistant-pipeline
  → no second pipeline; Buggy routes plug into existing routing/policy stages

assistant-api
  → B8 Buggy Control Center API endpoints/read models

assistant-panel
  → B8/B9 trust/realtime contracts reused; no browser authority

assistant-bootstrap
  → composition/wiring only
```

## 1.3 Dependency rule

```text
assistant-core
      ↑
assistant-buggy
      ↑
assistant-integrations / assistant-api / assistant-bootstrap
```

`assistant-buggy` stays framework-free and does not depend on Spring, JDBC, Panel libraries or vendor-specific APIs.

Where Buggy needs Foundation behavior, it consumes ports/types rather than reimplementing the subsystem.

---

# 2. Product versioning

The Foundation compatibility baseline remains `0.1.0-dev.18` forever as the accepted Phase-18 contract point.

Buggy development uses a separate product milestone nomenclature:

```text
B0 architecture
B1 identity runtime
...
B12 acceptance
```

The roadmap phase name must not be interpreted as a new Foundation phase. There is no Foundation Phase 19.

The source tree may continue carrying the Foundation build version until a separate release-number decision is made; the B-phase is recorded independently in Buggy checkpoint artifacts.

---

# 3. Canonical Bot Manifest schema

## 3.1 Manifest payload

Manifest schema version 1 is typed in `BuggyManifest` and represented by:

```yaml
bot:
  schema_version: 1
  id: buggy
  assistant_id: BUGGY
  display_name: Buggy
  service_identity: assistant:buggy
  type: OWNER_ASSISTANT
  scopes: [NETWORK_WIDE, OWNER_DIRECTED]

identity:
  persistent: true
  identity_core_protected: true
  primary_role: PERSONAL_OWNER_AND_NETWORK_OPERATIONS_ASSISTANT

profiles:
  capability: buggy-capabilities-v1
  policy: buggy-policy-v1
  knowledge: buggy-knowledge-v1
  runtime: buggy-runtime-v1
  personality: null   # B2 activates versioned personality state

authority:
  tier: HIGHEST_ASSISTANT_TIER
  security_authority: NETWORKCORE
  self_approval: false
  authority_self_escalation: false
  risk_downgrade: false

owner:
  binding_authority: NETWORKCORE
  binding_value: PROVISIONED_EXTERNALLY
```

## 3.2 Protected vs. versioned fields

Protected Identity Core fields:

```text
assistant_id
canonical_id
service_identity
assistant_type
primary_role
authority_tier
security_authority
scopes
core principles
```

Those fields are hard invariants in B1 and cannot be changed by Personality, Knowledge, Tasks, Tools or Self-Development.

Versioned configuration fields:

```text
display_name
capability_profile_id
policy_profile_id
knowledge_profile_id
runtime_profile_id
personality_profile_id
```

Security-bypass booleans are represented explicitly and must remain `false`:

```text
self_approval_allowed
authority_self_escalation_allowed
risk_downgrade_allowed
```

## 3.3 Manifest integrity

Every configuration revision receives a deterministic canonical payload and SHA-256.

Uses:

- persistence integrity;
- diff/history correlation;
- later request/approval binding where configuration mutations become privileged;
- Control Center history display;
- recovery verification.

---

# 4. Identity architecture

## 4.1 Identity layers

```text
Persistent Buggy Identity
    ├── protected Identity Core
    ├── active Owner binding revision
    └── active Bot configuration revision

Runtime Instance
    ├── runtime_instance_id
    ├── identity_head_revision
    ├── configuration_revision
    └── runtime_profile_id

Session Instance
    ├── session_instance_id
    ├── runtime_instance_id
    └── Foundation conversation_id
```

Invariant:

```text
Identity != Runtime != Session
```

A process restart changes the runtime instance ID only. A new chat/session changes the session ID only. Neither creates a new Buggy identity.

## 4.2 Owner binding

Owner binding persists a NetworkCore-backed `ActorId` reference.

It does not become a new authentication or authorization source.

```text
owner_actor_id
binding_authority = NETWORKCORE
binding_revision
binding_sha256
```

B1 deliberately exposes no public API/tool to mutate the Owner binding. Future rebinding is an OWNER_ONLY operation and must go through the normal Request/Approval + Audit path.

## 4.3 Fail-closed startup

Buggy product runtime is unavailable when the persistent identity has not been provisioned.

No anonymous/default Owner is invented.

The initial Owner `ActorId` must be supplied by controlled deployment/provisioning from the authoritative NetworkCore identity context.

---

# 5. Persistence architecture

## 5.1 Schema ownership

All Buggy product state is stored under the existing Foundation-owned logical schema:

```text
assistant.*
```

No `buggy.*` database schema is introduced because that would create unnecessary ownership and backup/grant complexity.

## 5.2 Migration numbering

Post-Foundation bot migrations use the `V100_xxx` range.

Reason:

- avoids implying a Foundation Phase 19;
- sorts after all Foundation migrations;
- leaves a clean post-Foundation product migration epoch.

Initial migration:

```text
V100_001__create_buggy_identity_runtime.sql
```

## 5.3 B1 tables

```text
assistant.buggy_identity_core
assistant.buggy_owner_binding_revisions
assistant.buggy_config_revisions
assistant.buggy_identity_head
```

Properties:

- Identity Core is immutable at database level;
- Owner binding revisions are append-only;
- configuration revisions are append-only;
- head row points to active immutable revisions;
- SHA-256 values are persisted for identity/config/binding integrity;
- no secret values are stored;
- initial provisioning is serialized by a fixed PostgreSQL transaction-scoped advisory lock so concurrent bootstrap attempts cannot fork the singleton identity.

## 5.4 B2 Personality persistence

B2 is implemented by `V100_002__create_buggy_personality_evolution.sql` using normalized append-only state under the existing `assistant.*` schema:

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

The Personal Development Journal is a read projection over immutable observations plus immutable Personality revisions. It is intentionally not a second mutable journal authority/store. Rollback appends a new revision pointing to an earlier target revision; historical revisions are never edited or reactivated in-place.

B3 — Capability bindings:

No second authority table. NetworkCore remains authoritative. Local data stores only profile/version metadata if needed for rendering/correlation.

B4 — Knowledge / Decision Memory:

Use Foundation Knowledge documents/revisions/ACL/provenance. Do not create a second Knowledge store. Buggy-specific binding/index metadata may reference `knowledge.document_id` only when needed.

B6 — Tasks / Activity / Automations:

```text
assistant.buggy_tasks
assistant.buggy_task_relations
assistant.buggy_activity
assistant.buggy_automation_policies
assistant.buggy_automation_runs
```

B7 — Self-Development:

```text
assistant.buggy_selfdev_items
assistant.buggy_selfdev_revisions
assistant.buggy_selfdev_evidence
```

Artifacts/source snapshots remain references to Source Registry/build artifacts; binaries are not stored in normal rows.

B8/B9 — Control Center:

No duplicate write store. APIs are projections over canonical domains.

B10 — Incidents/Briefings:

Incident Commander uses existing Bug/Incident references plus Buggy activity/task state. A dedicated incident projection is allowed only if it references, rather than replaces, authoritative incident/bug sources.

## 5.5 Secrets

Secrets are never persisted in:

```text
Identity Core
Owner binding
Manifest
Personality
Tasks
Activity
Knowledge
Self-Development records
```

Only secret references may appear. Secret values are injected at tool execution boundaries under Foundation secret policy.

---

# 6. Runtime architecture

## 6.1 Canonical request path

Every interactive Buggy request follows the existing Phase-13 path:

```text
Ingress
→ service authentication
→ actor resolution
→ assistant access authorization
→ conversation resolution
→ capability resolution
→ route resolution
→ policy
→ execution
→ response security
→ formatting
→ audit
→ response
```

Buggy does not get a shortcut around this pipeline.

## 6.2 Inference

Inference uses `InferencePort` from `assistant-integration-contracts`.

The model/provider is not identity.

```text
Buggy Identity
   ↓
Buggy runtime context
   ↓
InferencePort
   ↓
provider/model selected by runtime policy
```

Changing provider/model must not alter Buggy's persistent identity, owner binding, security authority or personality history.

## 6.3 Tools

All productive actions execute through the existing Tool Runtime.

```text
Buggy planner/orchestrator
→ ToolRegistry
→ ToolDescriptor
→ Foundation authorization
→ risk/approval policy
→ typed handler
→ output filter
→ audit
```

No direct `ProcessBuilder`, `Runtime.exec`, raw SSH, raw Docker exec, raw SQL-write or generic arbitrary-URL tool is allowed.

## 6.4 Proactive work

B6 introduces a scheduler/orchestrator that produces the same typed pipeline/tool invocations as interactive work.

It does not receive separate authority.

```text
Automation trigger
→ Buggy Task
→ policy/capability resolution
→ normal Tool Runtime / Request path
```

## 6.5 Self-Development

B7 uses isolated source/workspace/build tools only.

```text
Source Registry snapshot
→ controlled workspace
→ patch
→ build/test/security/benchmark
→ self-review
→ reject OR Request/Approval
→ controlled deployment
```

No self-authored change receives lower risk.

---

# 7. Integration architecture

## 7.1 Source

Reuse `SourceRegistryPort`.

Buggy stores snapshot/component/version/build references, never an uncontrolled source-of-truth copy.

## 7.2 Diagnostics

Reuse `DiagnosticReadPort` and typed Tool Runtime handlers.

No command-string diagnostics.

## 7.3 Controlled writes

Reuse `ControlledWritePort` plus Phase-9 exact approval binding.

HIGH/CRITICAL writes must be bound to the approved deterministic payload/revision.

## 7.4 NetworkCore

Existing `NetworkCoreAuthorizationAdapter` remains the authoritative access bridge.

Buggy's local capability profile is only a required-capability declaration; it cannot grant permissions by itself.

## 7.5 Knowledge

Reuse `KnowledgeCatalogService`/repository/search/ACL/publication workflow.

Buggy may create DRAFTs, but publication remains the Foundation Knowledge workflow.

## 7.6 Bugs and Requests

Reuse `BugService` and `RequestService`.

No Buggy-local approval or bug state machine.

## 7.7 Supervisor

Reuse `AssistantSupervisorReadPort` and typed Self-Healing service.

B11 adds clean Buggy coordinator contracts for future BYTE/KAESE without implementing their identities.

## 7.8 Panel

Browser → Panel Backend → Assistant API remains mandatory.

No Browser/Control Center code may trust client-supplied permissions or access the Assistant database directly.

## 7.9 Realtime

Reuse bounded/sanitized SSE contracts from Phase 15.

Buggy product event types are projected into the existing realtime boundary; a second websocket/event authority is not created.

---

# 8. Security and capability architecture

## 8.1 Capability rule

Capabilities express requested authority; NetworkCore permissions grant authoritative access.

```text
Buggy profile allows capability
AND NetworkCore actor permission allows
AND resource authorization allows
AND operational mode allows
AND kill switches allow
AND risk/approval policy allows
```

Otherwise deny/fail closed.

## 8.2 Existing Foundation capabilities reused immediately

```text
assistant.knowledge.query
assistant.bug.create
assistant.bug.read
assistant.bug.update
assistant.incident.read
assistant.source.read
assistant.diagnostics.read
assistant.supervisor.observe
assistant.supervisor.report
assistant.selfheal.low
assistant.external.request
assistant.change.request
assistant.write.request
assistant.approval.review
assistant.approval.critical
assistant.admin.view
assistant.admin.manage
```

## 8.3 B3 capability extensions fixed by B0

The following product capabilities are the target B3 additions. They extend the existing catalog; they do not create another security system.

| Capability | NetworkCore permission | Default risk/nature |
|---|---|---|
| `assistant.source.workspace.mutate` | `networkcore.assistant.source.workspace.mutate` | MEDIUM / MUTATING |
| `assistant.build.execute` | `networkcore.assistant.build.execute` | LOW / MUTATING |
| `assistant.deployment.inspect` | `networkcore.assistant.deployment.inspect` | LOW / READ |
| `assistant.deployment.stage` | `networkcore.assistant.deployment.stage` | MEDIUM / MUTATING |
| `assistant.deployment.production.request` | `networkcore.assistant.deployment.production.request` | HIGH / MUTATING |
| `assistant.infrastructure.read` | `networkcore.assistant.infrastructure.read` | MEDIUM / READ |
| `assistant.infrastructure.recovery.low` | `networkcore.assistant.infrastructure.recovery.low` | LOW / MUTATING |
| `assistant.database.read` | `networkcore.assistant.database.read` | HIGH / READ |
| `assistant.database.backup` | `networkcore.assistant.database.backup` | MEDIUM / MUTATING |
| `assistant.database.restore.request` | `networkcore.assistant.database.restore.request` | HIGH / MUTATING |
| `assistant.network.read` | `networkcore.assistant.network.read` | HIGH / READ |
| `assistant.security.read` | `networkcore.assistant.security.read` | HIGH / READ |
| `assistant.security.change.request` | `networkcore.assistant.security.change.request` | CRITICAL / MUTATING |
| `assistant.knowledge.draft` | `networkcore.assistant.knowledge.draft` | LOW / MUTATING |
| `assistant.selfdevelopment.workspace` | `networkcore.assistant.selfdevelopment.workspace` | MEDIUM / MUTATING |
| `assistant.minecraft.read` | `networkcore.assistant.minecraft.read` | MEDIUM / READ |
| `assistant.minecraft.execute` | `networkcore.assistant.minecraft.execute` | tool-specific / MUTATING |
| `assistant.discord.read` | `networkcore.assistant.discord.read` | MEDIUM / READ |
| `assistant.discord.execute` | `networkcore.assistant.discord.execute` | tool-specific / MUTATING |
| `assistant.web.read` | `networkcore.assistant.web.read` | MEDIUM / READ |
| `assistant.mail.read` | `networkcore.assistant.mail.read` | HIGH / READ |
| `assistant.mail.draft` | `networkcore.assistant.mail.draft` | MEDIUM / MUTATING |
| `assistant.mail.send.request` | `networkcore.assistant.mail.send.request` | HIGH / MUTATING |
| `assistant.panel.owner.read` | `networkcore.assistant.panel.owner.read` | HIGH / READ |

Direct security-authority mutation is intentionally absent. Security mutation stays Request/Owner gated through Foundation write/change mechanisms.

## 8.4 Owner-only operations

No ToolDescriptor for an OWNER_ONLY operation may be registered as autonomously executable.

Owner-only examples remain:

```text
Buggy authority change
role/permission mutation
NetworkCore policy mutation
approval-policy weakening
kill-switch override
irreversible production deletion
backup destruction
audit-integrity change
critical firewall/routing mutation
```

---

# 9. Initial typed Tool Catalog fixed by B0

B5 implements handlers/descriptors. B0 fixes the initial names and categories.

## 9.1 Diagnostics

```text
diagnostics.service.status
diagnostics.service.health
diagnostics.logs.search
diagnostics.logs.tail
diagnostics.logs.correlate
diagnostics.resource.cpu
diagnostics.resource.memory
diagnostics.resource.disk
diagnostics.resource.network
diagnostics.dependency.check
diagnostics.minecraft.server
diagnostics.velocity.proxy
diagnostics.postgres.health
diagnostics.container.inspect
```

## 9.2 Source

```text
source.read
source.search
source.diff
source.history
source.dependencies
source.analyze
source.create_workspace
source.modify_workspace
source.create_patch
source.compare_builds
source.static_analysis
```

## 9.3 Build/Test

```text
build.gradle
build.maven
build.java
build.test
build.integration_test
build.security_test
build.benchmark
build.jdeps
build.package
build.verify_artifact
build.sha256
```

These are typed build operations in isolated/bounded build environments, not a production shell.

## 9.4 Deployment

```text
deployment.inspect
deployment.plan
deployment.stage
deployment.verify
deployment.rollback
deployment.production
deployment.status
deployment.compare
```

`deployment.production` is HIGH and approval-gated.

## 9.5 Infrastructure

```text
infrastructure.host.status
infrastructure.container.status
infrastructure.container.restart
infrastructure.service.restart
infrastructure.disk.inspect
infrastructure.network.inspect
infrastructure.resource.inspect
infrastructure.config.inspect
```

Only explicitly classified LOW recovery actions may become autonomous.

## 9.6 Database

```text
database.health
database.schema.inspect
database.query.readonly
database.explain
database.performance
database.connections
database.locks
database.migration.inspect
database.backup
database.verify_backup
database.restore_plan
```

No `sql.execute_raw`.

## 9.7 Network

```text
network.dns.inspect
network.port.check
network.latency
network.route.inspect
network.tls.inspect
network.service_connectivity
network.firewall.inspect
network.proxy.inspect
```

## 9.8 Security

```text
security.audit.search
security.permission.inspect
security.actor.inspect
security.capability.inspect
security.session.inspect
security.token.metadata
security.policy.inspect
security.incident.inspect
```

Mutations are request-only workflows, not direct autonomous security tools.

## 9.9 Knowledge

```text
knowledge.search
knowledge.read
knowledge.create_draft
knowledge.revise
knowledge.detect_conflicts
knowledge.mark_stale
knowledge.attach_source
knowledge.request_publish
```

## 9.10 Bugs / Requests / Supervisor

```text
bugs.search
bugs.inspect
bugs.create
bugs.update
bugs.relate
bugs.duplicate
bugs.regression
bugs.attach_build
bugs.attach_incident
bugs.triage
bugs.resolve
bugs.reopen

requests.create
requests.inspect
requests.update_payload
requests.cancel
requests.observe_status

assistant.status
assistant.health
assistant.metrics
assistant.failures
assistant.degrade
assistant.restart
assistant.pause
assistant.resume
assistant.inspect_configuration
assistant.inspect_tools
assistant.inspect_knowledge
```

## 9.11 Self-Development

```text
selfdev.observe
selfdev.create_proposal
selfdev.create_workspace
selfdev.modify_source
selfdev.build
selfdev.test
selfdev.benchmark
selfdev.security_scan
selfdev.compare
selfdev.reject_own_proposal
selfdev.submit_for_approval
```

## 9.12 Minecraft / Discord / Web / Mail / Panel

These remain granular typed tools. B5 must not introduce universal pass-through calls.

Web tools must enforce SSRF-safe destination policy, redirect revalidation, private/metadata network blocking, content-type/size/time limits and egress allowlisting.

Mail SEND stays context/risk dependent; READ/DRAFT may be broader.

---

# 10. Task, Activity and Automation architecture

B6 introduces durable work independent of a chat session.

## 10.1 Task state

Target states:

```text
OPEN
RUNNING
WAITING_DEPENDENCY
WAITING_OWNER
BLOCKED
COMPLETED
FAILED
CANCELLED
```

A task may reference:

```text
conversation
bug
incident
request
build
source snapshot
self-development item
parent/subtask
```

## 10.2 Activity

Activity is owner-readable work history and is not Security Audit.

Activity entries reference central Audit event IDs when a security-relevant audited action exists.

## 10.3 Automation

Automation policies contain:

```text
trigger
action/route
risk ceiling
resource scope
budget
cooldown
max attempts
timeout
concurrency
attention level
enabled state
last/next run
```

Automation execution uses normal Foundation authorization/tool/audit paths.

---

# 11. Self-Development architecture

B7 lifecycle:

```text
OBSERVED
PROPOSAL
IMPLEMENTING
TESTING
REJECTED_BY_BUGGY
WAITING_FOR_OWNER_APPROVAL
APPROVED
DEPLOYED
VERIFIED
ROLLED_BACK
```

Each item stores references to:

```text
source snapshot
workspace
patch/diff
build artifact
unit/integration/security test evidence
benchmark evidence
risk classification
approval request
rollout/rollback evidence
```

The stored record does not grant deploy authority.

---

# 12. API architecture

## 12.1 Existing Foundation API remains

```text
/api/assistant/v1/...
```

B8 adds Buggy product surfaces beneath the same versioned API authority, for example:

```text
GET  /api/assistant/v1/buggy/overview
GET  /api/assistant/v1/buggy/identity
GET  /api/assistant/v1/buggy/personality
GET  /api/assistant/v1/buggy/tasks
GET  /api/assistant/v1/buggy/activity
GET  /api/assistant/v1/buggy/incidents
GET  /api/assistant/v1/buggy/self-development
GET  /api/assistant/v1/buggy/automations
GET  /api/assistant/v1/buggy/tools
GET  /api/assistant/v1/buggy/security
```

Unsafe POSTs must enter the normal Phase-13 pipeline and use idempotency.

## 12.2 Emergency controls

Emergency controls remain outside Buggy's own authority and map to existing Foundation control-plane capabilities/modes.

Control Center may render them, but Buggy cannot autonomously invoke privileged emergency changes against policy.

---

# 13. Panel / Control Center architecture

B9 presents three planes:

```text
COMMAND
OBSERVABILITY
EVOLUTION
```

Navigation target:

```text
Overview
Chat
Activity
Tasks
Approvals
Incidents
Bugs
Knowledge
Self-Development
Automations
Tools
Assistants
Infrastructure
Deployments
Security
Memory / Personality
Settings
Audit
```

Rules:

- Panel Backend calls Assistant API;
- no direct DB access;
- no trusted browser permissions;
- SSE only carries sanitized bounded events;
- Audit is immutable/non-editable;
- Identity Core is read-only;
- emergency controls are visibly separate from Buggy autonomous controls.

---

# 14. Event model

Use existing in-process/realtime infrastructure.

Planned product events include:

```text
BuggyIdentityProvisioned
BuggyConfigurationActivated
BuggyPersonalityRevisionCreated
BuggyTaskCreated
BuggyTaskStateChanged
BuggyActivityRecorded
BuggyAutomationTriggered
BuggyAutomationSuppressed
BuggySelfDevelopmentObserved
BuggySelfDevelopmentSubmitted
BuggySelfDevelopmentRejected
BuggyIncidentModeEntered
BuggyIncidentModeExited
BuggyOwnerDecisionRequired
```

Security-relevant events must additionally produce central Foundation Audit according to owning subsystem rules.

---

# 15. B0/B1 acceptance matrix

| Requirement | Architecture | B1 implementation |
|---|---|---|
| no duplicate Foundation authority | fixed | PASS |
| `buggy` persistent identity | fixed | PASS |
| `assistant:buggy` service identity | fixed | PASS |
| protected Identity Core | fixed | PASS — Java invariants + DB immutability trigger |
| Owner binding | NetworkCore ActorId revision | PASS — persisted/revisioned model |
| versioned Bot config | manifest revision + SHA-256 | PASS |
| identity/runtime/session separation | three explicit layers | PASS |
| personality evolution | B2 | NOT STARTED by design |
| capability expansion | B3 | NOT STARTED by design |
| Knowledge/Decision Memory | B4 | IMPLEMENTED / SOURCE-VERIFIED |
| typed productive tools | B5 | NOT STARTED by design |
| Tasks/Activity/Automation | B6 | NOT STARTED by design |
| Self-Development lab | B7 | NOT STARTED by design |
| Control Center backend | B8 | NOT STARTED by design |
| Control Center frontend | B9 | NOT STARTED by design |
| exclusive Buggy features | B10 | NOT STARTED by design |
| BYTE/KAESE supervisor readiness | B11 | NOT STARTED by design |
| final hardening | B12 | NOT STARTED by design |

---

# 16. B0 final gate

B0 is satisfied when:

```text
[x] concrete module map fixed
[x] Bot Manifest schema fixed
[x] persistence ownership/migration plan fixed
[x] runtime architecture fixed
[x] integration architecture fixed
[x] API/Panel surface map fixed
[x] Security/Capability mapping fixed
[x] initial typed Tool Catalog fixed
[x] acceptance matrix fixed
[x] no duplicate Foundation subsystem introduced
```

**B0 status: COMPLETE.**

The implementation proceeds with B1 on this architecture.
