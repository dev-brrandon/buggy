# Phase 17 Deployment & Operations

This directory is the canonical deployment/operations contract for `0.1.0-dev.19`.

- `kubernetes/base/`: non-root, read-only-root deployment with health probes and default-deny networking.
- `postgres/`: atomic backup, checksum verification, and guarded restore scripts for Assistant-owned schemas.
- `runbooks/`: staging, recovery, graceful shutdown, restart, dependency outage, rollback, and secret rotation procedures.

No Docker socket, host secret mount, privileged container, arbitrary shell execution endpoint, or external-analysis egress rule is included.

## G1.2 Runtime Completion

The G1.2 production runtime activation contract is documented in `runbooks/G1-2-RUNTIME-COMPLETION.md`.
`g1-2-runtime.env.example` lists variable names only; it intentionally contains no secrets.

The NetworkCore authority listener remains loopback-only. Cross-host access must terminate TLS at an internal service edge and preserve the request-bound HMAC headers.
