# G0 Change Manifest

## Runtime-discovered code hardening

- `assistant-buggy/.../BuggyPersonalityRevision.java`
  - permits rollback revisions to identify attributes removed by the rollback target.
- `assistant-buggy/.../B2BuggyPersonalityEvolutionSelfTest.java`
  - adds regression coverage for rollback removing a post-target attribute.

## PostgreSQL harness compatibility

- `assistant-integrations/.../Phase7PostgresKnowledgeSelfTest.java`
  - isolated cleanup uses `TRUNCATE ... CASCADE` after B4 introduced a valid FK to Knowledge documents.
- `assistant-integrations/.../B6BuggyTasksAutomationPostgresSelfTest.java`
  - uses final B2 table name `buggy_personality_observation_evidence`.
- `assistant-integrations/.../B7BuggySelfDevelopmentPostgresSelfTest.java`
  - uses final B2 table name `buggy_personality_observation_evidence`.

## Documentation / track state

- adds the G0 runtime acceptance report;
- adds the canonical G1 initial live integration scope;
- advances `CURRENT-TRACK.txt` from pending external environment gates to G0 runtime accepted / G1 next.

## Explicit non-changes

- no new migration;
- no new Foundation authority;
- no new Buggy capability;
- no BYTE implementation;
- no KAESE implementation;
- no G1 channel integration implemented during G0.
