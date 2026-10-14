# Buggy Native Core — 8 Development Blocks

Decision date: 2026-10-06
Status: Canonical implementation roadmap

## Guiding principle

```text
Buggy is Buggy.
Models, tools, NetworkCore and other services are resources used by Buggy.
They are not Buggy's identity.
```

Blocks 1–6 must make Buggy independently useful without an external LLM/API. Blocks 7–8 provide expansion frameworks and are activated incrementally.

## Block 1 — G1.4 Native Core Foundation

- Identity/Self-model foundation
- protected Core Protocol Catalog
- Birth Record
- BOOTSTRAP maturity baseline
- multidimensional maturity model
- bounded Baby-Buggy personality baseline
- initial self-capability state
- immutable persistence baseline

Status: **IMPLEMENTED / SOURCE-VERIFIED / POSTGRESQL-18.6-VERIFIED / NOT LIVE**

## Block 2 — G1.5 Memory & Development

- Session Memory
- Experience Memory
- Knowledge Memory
- Relationship Memory
- deep Primary-Owner Memory
- provenance and confidence
- retention / pinning / superseding
- Reflection
- Learning Candidates
- Development Journal

Status: **IMPLEMENTED / SOURCE-VERIFIED / POSTGRESQL-18.6-VERIFIED / NOT LIVE**

## Block 3 — G1.6 Natural Conversation

- Conversation Context
- Intent Engine
- Entity Resolver
- Response Plan
- Natural Response Composer
- initial Buggy voice/persona without external inference

Status: **IMPLEMENTED / SOURCE-VERIFIED / NOT LIVE**

## Block 4 — G1.7 Native Knowledge & Read-Only Tools

- NetworkCore / network / server / service knowledge
- incident/status data
- first deterministic diagnosis rules
- read-only tool registry and tools
- no destructive actions

## Block 5 — G1.8 Observation & Presence

- System Observation Engine
- Social Context Engine
- channel-scoped disclosure projections
- PRIVATE
- PUBLIC_MENTION
- PUBLIC_ACTIVE
- OBSERVE
- Participation Engine
- public Discord/Minecraft/Panel behavior

## Block 6 — G1.9 Learning & Maturity

- Experience -> Reflection -> Learning
- behavior rules with evidence/confidence
- bounded Personality microchanges
- competence and confidence separation
- BOOTSTRAP -> CHILD -> APPRENTICE -> YOUNG -> MATURE gates
- Owner approval for maturity transitions
- proactive Owner communication (ALERT/NOTICE/FOLLOW_UP/SHARE)

## Block 7 — G1.10 Model Gateway

Framework is implemented as part of the eight-block architecture, but provider access is enabled later.

Profiles:

```text
CHAT
REASONING
CODE
```

DeepSeek is the chosen future provider. Concrete provider model identifiers are configuration, not hard-coded Buggy identity.

Initial deployment after framework implementation may remain:

```text
provider = NONE
api_key = NOT_CONFIGURED
```

Buggy must continue functioning natively.

## Block 8 — G1.11+ Advanced Buggy

Build the secure framework first; add powerful tools incrementally afterwards.

Potential progression:

1. source read/analysis;
2. Improvement Proposals;
3. Patch Candidates;
4. tests/build candidates;
5. controlled deployment preparation;
6. selected write tools much later with NetworkCore Authority + explicit approvals.

Never allowed:

```text
Buggy detects change
-> modifies live source
-> self-authorizes deploy
```

## Permanent security separation

```text
Conversation Access
!= Information Visibility
!= Tool Permission
!= Authority
```

NetworkCore remains Authority regardless of maturity, personality, memory, learned rules or model output.
