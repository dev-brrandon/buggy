# B3 — BUGGY Authority & Capability Bindings — Implementation Report

**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Date:** 2026-10-04
**Foundation compatibility baseline:** `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
**Canonical Buggy profile:** `buggy-capabilities-v1`
**Security authority:** NetworkCore via the existing `assistant-security` authorization pipeline

---

## 1. Scope completed

B3 binds the canonical BUGGYBOT-SPEC-V1 capability model to the already accepted Foundation security contracts.

Implemented:

- all 20 canonical Buggy V1 capability groups;
- 39 atomic `assistant.buggy.*` capabilities;
- explicit READ / EXECUTE / MUTATE / REQUEST_ONLY product bindings;
- explicit OWNER_ONLY boundary markers that are never directly granted as Buggy capabilities;
- NetworkCore permission mapping for every atomic capability;
- canonical `buggy-capabilities-v1` profile;
- descriptor-aware profile binding inside the existing `AssistantCapabilityPolicy` contract;
- fail-closed rejection when Buggy presents a non-canonical capability profile;
- global emergency-control enforcement for every B3 capability;
- cross-module verification that the B1 manifest points at the exact B3 capability profile.

No new authentication, permission, role, approval, risk, execution, persistence, or security authority was introduced.

---

## 2. Authority chain

The runtime authority chain remains:

```text
Authenticated service principal
        ↓
Authoritative actor resolution
        ↓
NetworkCore security snapshot
        ↓
networkcore.assistant.use.buggy
        ↓
buggy-capabilities-v1 profile membership
        ↓
capability-specific NetworkCore permission
        ↓
resource authorization
        ↓
Foundation operational mode / feature / kill switch
        ↓
existing Tool Runtime risk + approval policy (B5 tools)
```

Buggy's product profile is only an allow-list of capabilities that Buggy may ask the Foundation to authorize. It is not an authority source and cannot override NetworkCore.

A capability is therefore denied when any required Foundation gate denies it.

---

## 3. Canonical profile

Profile ID:

```text
buggy-capabilities-v1
```

Assistant binding:

```text
BUGGY only
```

Namespace:

```text
assistant.buggy.*
```

NetworkCore capability permission namespace:

```text
networkcore.assistant.buggy.*
```

Assistant-use gate:

```text
networkcore.assistant.use.buggy
```

The B1 `BuggyManifest.initial()` already carries `buggy-capabilities-v1`. B3 now makes this binding enforceable rather than descriptive only.

---

## 4. Capability bundles

| Canonical group | Atomic capabilities | Authority intent |
|---|---|---|
| `operations.*` | `assistant.buggy.operations.execute.low`, `assistant.buggy.operations.mutate.low` | EXECUTE / MUTATE, LOW only |
| `diagnostics.*` | `assistant.buggy.diagnostics.read`, `assistant.buggy.diagnostics.execute` | READ / EXECUTE |
| `infrastructure.*` | `assistant.buggy.infrastructure.read`, `assistant.buggy.infrastructure.recovery.low`, `assistant.buggy.infrastructure.request` | READ / constrained recovery / REQUEST_ONLY |
| `source.*` | `assistant.buggy.source.read`, `assistant.buggy.source.workspace.mutate` | READ / controlled workspace MUTATE |
| `build.*` | `assistant.buggy.build.execute` | EXECUTE |
| `deployment.*` | `assistant.buggy.deployment.read`, `assistant.buggy.deployment.nonprod.execute`, `assistant.buggy.deployment.production.request` | READ / non-prod EXECUTE / production REQUEST_ONLY |
| `database.*` | `assistant.buggy.database.read`, `assistant.buggy.database.write.request` | READ / REQUEST_ONLY |
| `network.*` | `assistant.buggy.network.read`, `assistant.buggy.network.mutate.request` | READ / REQUEST_ONLY |
| `security.read.*` | `assistant.buggy.security.read` | controlled READ |
| `security.request.*` | `assistant.buggy.security.request` | REQUEST_ONLY |
| `knowledge.*` | `assistant.buggy.knowledge.read`, `assistant.buggy.knowledge.draft.mutate` | READ / DRAFT MUTATE |
| `bugs.*` | `assistant.buggy.bugs.read`, `assistant.buggy.bugs.mutate` | READ / MUTATE |
| `requests.*` | `assistant.buggy.requests.read`, `assistant.buggy.requests.create` | READ / CREATE |
| `assistant.supervisor.*` | `assistant.buggy.supervisor.read`, `assistant.buggy.supervisor.execute` | READ / constrained EXECUTE |
| `assistant.selfdevelopment.*` | `assistant.buggy.selfdevelopment.workspace.mutate`, `assistant.buggy.selfdevelopment.execute` | sandbox/workspace MUTATE / EXECUTE |
| `panel.*` | `assistant.buggy.panel.read`, `assistant.buggy.panel.execute` | READ / bounded EXECUTE |
| `discord.*` | `assistant.buggy.discord.read`, `assistant.buggy.discord.execute.low` | READ / LOW EXECUTE |
| `minecraft.*` | `assistant.buggy.minecraft.read`, `assistant.buggy.minecraft.execute.low` | READ / LOW EXECUTE |
| `web.*` | `assistant.buggy.web.read`, `assistant.buggy.web.execute` | READ / bounded EXECUTE |
| `mail.*` | `assistant.buggy.mail.read`, `assistant.buggy.mail.send.request` | READ / REQUEST_ONLY |

The capability group describes product responsibility. The atomic capability plus the existing Foundation security rule determines actual technical authorization.

---

## 5. Risk-floor and emergency-control rules

B3 security rules are registered in the existing `AssistantSecurityCatalog` through `BuggySecurityCatalog`.

Important properties:

- every atomic B3 capability has exactly one canonical Foundation `CapabilitySecurityRule`;
- each rule requires a NetworkCore permission;
- each B3 rule obeys `ALL_TOOLS_OFF`;
- mutating capabilities continue to obey `READ_ONLY`;
- every capability is denied in `MAINTENANCE`;
- diagnostics additionally obey `DIAGNOSTICS_OFF`;
- low recovery additionally obeys `SELF_HEALING_OFF`;
- write-like operations additionally obey `WRITE_ACTIONS_OFF` where they are executable mutations rather than request creation;
- Supervisor bindings reuse the existing `SUPERVISOR` feature gate;
- Source read reuses the existing `SOURCE_READ` feature gate.

The B3 risk classification is the minimum capability risk floor. B5 ToolDescriptors may classify individual tools at a higher risk, but never lower than the canonical capability rule.

---

## 6. REQUEST_ONLY semantics

B3 deliberately distinguishes requesting a privileged operation from executing that operation.

Examples:

```text
assistant.buggy.deployment.production.request
assistant.buggy.database.write.request
assistant.buggy.network.mutate.request
assistant.buggy.security.request
assistant.buggy.mail.send.request
```

Those bindings authorize creation/preparation of a request through the existing Foundation Request/Approval system. They do not provide direct production mutation capability.

The eventual B5 tool handlers for these capabilities must therefore terminate at the Request/Approval boundary rather than performing the requested privileged operation directly.

---

## 7. OWNER_ONLY boundary

B3 explicitly records the following V1 Owner-only operations without exposing direct Buggy capabilities for them:

- alter security authority;
- change Buggy's own authority;
- change roles or permissions;
- change NetworkCore security rules;
- weaken approval policy;
- rotate critical production credentials;
- irreversible production data deletion;
- destroy backups;
- alter audit integrity;
- disable global kill switches;
- critical firewall/routing change;
- permanently delete another assistant;
- bypass classification/security controls.

Buggy may analyze, design, prepare and create a request for such work where a typed request path exists. Buggy cannot self-authorize the action.

`OWNER_ONLY` is intentionally a boundary marker rather than a capability grant.

---

## 8. Profile binding hardening

The existing `AssistantCapabilityPolicy` received a backwards-compatible descriptor-aware default method.

Legacy Foundation policies retain their existing behavior.

The canonical `BuggyCapabilityPolicy` overrides the descriptor-aware method and requires all of the following:

```text
assistant_id == BUGGY
capability_profile_id == buggy-capabilities-v1
capability ∈ canonical B3 profile
```

`AssistantAuthorizationService` now invokes this descriptor-aware policy hook.

Consequences:

- a tampered or unknown Buggy capability profile fails closed;
- BYTE and KAESE receive no B3 capability through this policy;
- NetworkCore permission checks remain authoritative after profile membership succeeds.

---

## 9. Persistence

No B3 persistence migration is required.

Reason:

- the selected capability profile ID is already persisted as part of the B1 versioned Buggy configuration;
- security decisions must use live authoritative NetworkCore state rather than a copied Assistant-side permission database;
- the B3 profile itself is canonical versioned source configuration, not mutable runtime authority state.

This avoids creating a stale or competing permission store.

---

## 10. Foundation changes

B3 makes only the architecture-approved `assistant-security` extension:

- adds Buggy capability/profile metadata;
- adds Buggy capability rules to the existing catalog;
- adds the backwards-compatible descriptor-aware capability-policy hook;
- changes authorization to call that hook;
- relaxes old verification assertions from "catalog equals Phase-0 namespace" to "catalog retains all accepted Phase-0 capabilities", allowing approved product extensions while protecting Foundation coverage.

No accepted Foundation capability was removed or weakened.

---

## 11. Verification

Verified locally with:

```text
javac -encoding UTF-8 -Xlint:all,-processing -Werror
```

Passed:

- complete main-source compile: 477 Java files;
- complete self-test-source compile: 30 Java files;
- Phase-3 Security Foundation regression;
- B1 Buggy Identity Runtime regression;
- B2 Buggy Personality Evolution regression;
- B3 Authority/Capability self-test;
- B3 B1-manifest ↔ B3-profile integration self-test;
- Phase-10 Tool Runtime regression;
- Phase-18 Foundation Final Verification regression.

B3-specific verification covers:

- all 20 canonical groups present exactly once;
- all 39 atomic capabilities present exactly once;
- every B3 capability has a Foundation security rule;
- every B3 capability is NetworkCore-permission-backed;
- `networkcore.assistant.use.buggy` remains mandatory;
- wrong capability profile fails closed;
- permission revocation is immediately respected;
- BYTE/KAESE are not granted Buggy capabilities;
- `ALL_TOOLS_OFF`, `READ_ONLY`, and `MAINTENANCE` override Buggy;
- OWNER_ONLY operations have no direct Buggy capability.

---

## 12. Environment-dependent gates

Still not claimed as passed in this environment:

- canonical JDK 25 + Gradle 9.6.1 full `qualityCheck`;
- live PostgreSQL 18.6 regressions inherited from B1/B2.

The Gradle wrapper remains pinned to 9.6.1, but the distribution is not locally cached and network access to `services.gradle.org` is unavailable. The local compiler is OpenJDK 21.0.11.

B3 itself introduces no PostgreSQL migration or repository and therefore has no new B3-specific database gate.

---

## 13. Next roadmap item

**B4 — Knowledge and Decision Memory**

B4 must build Buggy's broad Knowledge bindings, Owner Knowledge and Decision Memory on the existing Foundation Knowledge lifecycle, ACL, provenance and publication contracts. Secrets remain excluded from normal Knowledge.
