# Legit Assistant Phase 17 Operations Runbook

## Recovery objectives

- RPO <= 15 minutes.
- RTO <= 60 minutes.
- Schedule PostgreSQL backups at least every 15 minutes or use WAL/PITR with an equivalent or better RPO.
- Restore drills must be executed after schema changes and before a production promotion.

## Staging deployment

1. Build and verify `0.1.0-dev.19` with Java 25 / Gradle 9.6.1.
2. Deploy the immutable image as UID/GID 10001 with a read-only root filesystem.
3. Inject secrets only from the runtime secret store; never bake them into the image or mount host secret directories.
4. Keep the management endpoint on loopback.
5. Apply default-deny ingress/egress policies before enabling explicit Panel, DNS, PostgreSQL and NetworkCore traffic.
6. Require `/health/live` HTTP 200 before continuing.
7. Require `/health/ready` HTTP 200 before routing production traffic. A 503 is a valid fail-closed signal while required dependencies are unavailable.

## Backup

`deployment/postgres/backup-postgres.sh` backs up the Assistant-owned schemas `assistant`, `bugs`, `knowledge`, `requests`, and `audit` as a PostgreSQL custom-format archive. Each archive is written atomically, receives a SHA-256 sidecar, and has its TOC validated by `pg_restore` before success is reported.

Backups must be encrypted at rest by the platform storage layer and copied outside the failure domain of the primary PostgreSQL instance.

## Restore

1. Create a clean target database on the same or a newer supported PostgreSQL 18.x patch level.
2. Verify the archive with `verify-backup.sh`.
3. Set `LEGIT_ASSISTANT_RESTORE_CONFIRM=RESTORE` explicitly.
4. Run `restore-postgres.sh` against the clean target.
5. Compare schema/data verification counts and application readiness before promotion.
6. Never restore over the live primary in-place during a drill.

## Graceful shutdown

Kubernetes sends SIGTERM and allows 30 seconds. Spring graceful shutdown has a bounded 20-second phase timeout. Stop routing traffic first, then require the process to exit before the Kubernetes termination grace period ends. Forced SIGKILL is a failure of this gate.

## Restart safety

Runtime state that must survive a restart belongs in PostgreSQL or the authoritative external systems. Process-local replay/idempotency caches are intentionally not claimed as distributed durable state. After restart, liveness must recover and readiness remains fail-closed until required dependencies recover.

## Dependency outage

A NetworkCore or database outage must not turn into implicit authorization. `/health/live` may remain UP while `/health/ready` returns 503. Route-specific partial-degradation semantics remain in effect; only surfaces whose required dependencies are unavailable are degraded.

## Rollback

1. Keep the previous immutable runtime/image and its SHA-256 digest available.
2. Do not roll database schemas backward destructively. Phase 17 adds no migration.
3. Remove the new instance from traffic, deploy the previous known-good runtime, and wait for readiness.
4. If data recovery is required, restore to a separate database first and promote only after verification.
5. Record the rollback and root cause through the canonical audit/incident path.

## Secret rotation

Panel HMAC service/context keys are runtime secrets. Rotate them through the platform secret store; do not place keys in Git, images, ConfigMaps, command-line arguments, or host-mounted dotenv files.
