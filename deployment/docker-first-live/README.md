# Legit Assistant — First Live Docker Acceptance

This stack is deliberately scoped to the **central Assistant service**. The dedicated Discord BuggyBot is not started here.

## What this first pass proves

1. Java 25 image build and the canonical Gradle `qualityCheck`.
2. PostgreSQL 18 startup.
3. Ordered Foundation + Buggy SQL migrations.
4. Least-privilege runtime database role (`legit_assistant_app`).
5. Idempotent persistent Buggy identity provisioning with the configured PRIMARY_OWNER.
6. Assistant boot and liveness.
7. Real database readiness probing.
8. Real NetworkCore-authority readiness probing when configured.
9. Fail-closed behavior while NetworkCore authority / inference / bridge secrets are absent.

## Safety properties

- PostgreSQL has no published host port.
- Assistant HTTP is published on host loopback only (`127.0.0.1:18080` by default).
- Management port 9091 stays loopback-only inside the Assistant container.
- Assistant runs as UID/GID 10001 with a read-only root filesystem and no Linux capabilities.
- `.env` is excluded from the Docker build context and must not be committed.
- Database owner/migrator/runtime roles remain separated.

## First boot

```bash
cd deployment/docker-first-live
./preflight.sh
./generate-first-live-env.sh
# inspect/edit .env; boot-only acceptance may leave external integration values blank

docker compose --env-file .env build assistant
docker compose --env-file .env up -d
./first-live-check.sh
```

For a boot-only test, `/health/live` must return HTTP 200 while `/health/ready` may correctly return HTTP 503 because NetworkCore is deliberately not yet connected.

## Logs

```bash
docker compose --env-file .env logs --tail=250 assistant
docker compose --env-file .env logs --tail=250 postgres
```

## Connect NetworkCore authority

Set both values in `.env` and recreate the Assistant container:

```text
LEGIT_NETWORKCORE_ASSISTANT_AUTHORITY_URL=https://<internal-authority-edge>/
LEGIT_ASSISTANT_NETWORKCORE_AUTHORITY_HMAC_KEY_B64=<same dedicated authority key configured in NetworkCore>
```

Cross-host plaintext HTTP is rejected. NetworkCore itself keeps its authority listener loopback-only; expose the two authority paths through an internal TLS edge as specified by the G1.2 runbook.

After configuration:

```bash
docker compose --env-file .env up -d --force-recreate assistant
./first-live-check.sh
```

At that point `/health/ready` must become HTTP 200 if PostgreSQL and NetworkCore are healthy.

## Connect inference

Set the OpenAI-compatible inference endpoint/model and, when required, its API key:

```text
LEGIT_ASSISTANT_INFERENCE_URL=...
LEGIT_ASSISTANT_INFERENCE_MODEL=...
LEGIT_ASSISTANT_INFERENCE_API_KEY=...
LEGIT_ASSISTANT_INFERENCE_ALLOW_EXTERNAL=true|false
```

Do not set `ALLOW_EXTERNAL=true` unless the configured endpoint is intentionally external and TLS-protected.

## Reset this disposable first-live database

This destroys the first-live Assistant database volume:

```bash
docker compose --env-file .env down -v
```

Do not use `down -v` once the database contains data you intend to retain.
