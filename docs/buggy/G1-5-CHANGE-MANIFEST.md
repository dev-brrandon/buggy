# G1.5 Change Manifest

## Product version

- source candidate: `0.1.0-dev.20`
- live First-Live runtime remains: `0.1.0-dev.18-first-live`

## Native Memory domain

Added under `assistant-buggy`:

- Memory IDs, types, provenance, retention and state contracts;
- Memory Candidate and Candidate Decision lifecycle;
- append-only Memory Revision contract;
- `BuggyMemoryRepository`;
- `BuggyMemoryService` with actor-projected information visibility and Primary-Owner isolation.

## Native Development domain

Added under `assistant-buggy`:

- Reflection records;
- Learning Candidates;
- append-only current-self capability revisions;
- Native Development Journal entries;
- `BuggyDevelopmentRepository`;
- `BuggyDevelopmentService`.

Learning Candidates remain proposals only. Personality/maturity evolution stays deferred to G1.9.

## Secret boundary

Added `FoundationSecretGuardedMemoryContentPolicy` in `assistant-integrations`, reusing the Foundation `SecretGuard` for persisted memory/development text and evidence references.

## Persistence

Added:

- `V100_007__create_buggy_memory_development.sql`
- `JdbcBuggyMemoryRepository`
- `JdbcBuggyDevelopmentRepository`

The migration creates append-only candidate-decision, memory-revision, reflection, learning, capability-revision and development-journal history plus bounded current-head projections.

## Runtime wiring

Updated `G12RuntimeConfiguration` to provide G1.5 content policy, repositories and services.

## Verification

Added:

- `G15BuggyMemoryDevelopmentSelfTest`
- `G15BuggyMemoryDevelopmentPostgresSelfTest`
- Gradle task `:assistant-buggy:g15MemoryDevelopmentSelfTest`
- Gradle task `:assistant-integrations:g15MemoryDevelopmentPostgresSelfTest`

Also added `G14BuggyNativeCorePostgresSelfTest` and its Gradle task to close the previously pending PostgreSQL-18 verification gate for the G1.4 baseline.

Canonical verification completed with JDK 25 / Gradle 9.6.1 and Percona PostgreSQL 18.6.1.

## B12 boundary

The reviewed Buggy migration allowlist is extended by exactly one migration:

```text
V100_001 .. V100_007
```

Unexpected additional `V100_*` migrations remain rejected.

## Explicitly unchanged

- G1.4 Birth/Native-Core baseline remains immutable;
- NetworkCore remains authority;
- existing Conversation and Knowledge stores remain authoritative;
- no DeepSeek/model configuration;
- no G1.6 Natural Conversation implementation;
- no G1.8 presence/observation modes;
- no G1.9 automatic personality/maturity learning;
- no G1.11 write/self-modification authority.
