# B12 Change Manifest

## Product code

No new productive subsystem or authority is added by B12.

## Verification

Added:

- `assistant-buggy/src/selfTest/java/de/legitserver/assistant/buggy/B12BuggyAcceptanceHardeningSelfTest.java`
- Gradle task `:assistant-buggy:acceptanceHardeningSelfTest`
- B12 task included in `:assistant-buggy:check`

The B12 gate verifies final Definition of Done, fail-closed emergency/authority behavior, forbidden escape hatches, Browser/Panel trust boundaries, the fixed migration set, deterministic concurrent read load and the B1–B11 Buggy-domain regression chain.

## Documentation

Added:

- `docs/buggy/B12-BUGGY-ACCEPTANCE-HARDENING-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B12-BUGGY-V1-ACCEPTANCE-MATRIX.md`
- `docs/buggy/B12-CHANGE-MANIFEST.md`

Updated:

- `docs/buggy/CURRENT-TRACK.txt`
- `README.md`

## Persistence

No B12 migration. Canonical Buggy migrations remain `V100_001` through `V100_005`.

## Foundation changes

None. B12 changes only `assistant-buggy` verification/build wiring plus Buggy documentation and the repository README.

## Future assistants

BYTE and KAESE remain `NOT_STARTED`.

## Remaining external gates

- canonical JDK 25 / Gradle 9.6.1 full quality build: `PENDING_TOOLCHAIN`
- PostgreSQL 18.6 live Buggy persistence regressions: `PENDING_DATABASE_RUNTIME`
