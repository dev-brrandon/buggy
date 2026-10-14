# G1.2 Runtime Completion — Deployment Contract

## Assistant environment

Required to activate the G1.2 production composition:

```text
LEGIT_ASSISTANT_POSTGRES_URL
LEGIT_ASSISTANT_POSTGRES_USER
LEGIT_ASSISTANT_POSTGRES_PASSWORD
```

Required for authoritative actor/security re-resolution:

```text
LEGIT_NETWORKCORE_ASSISTANT_AUTHORITY_URL
LEGIT_ASSISTANT_NETWORKCORE_AUTHORITY_HMAC_KEY_B64
```

Required for natural-language execution:

```text
LEGIT_ASSISTANT_INFERENCE_URL
LEGIT_ASSISTANT_INFERENCE_MODEL
LEGIT_ASSISTANT_INFERENCE_API_KEY        # optional for trusted local compatible endpoints
LEGIT_ASSISTANT_INFERENCE_ALLOW_EXTERNAL # must be true for non-loopback inference
```

Existing Panel and Minecraft bridge secrets remain required by their existing configuration.

## NetworkCore environment

```text
LEGIT_ASSISTANT_NETWORKCORE_AUTHORITY_HMAC_KEY_B64
LEGIT_NETWORKCORE_ASSISTANT_AUTHORITY_BIND_HOST=127.0.0.1
LEGIT_NETWORKCORE_ASSISTANT_AUTHORITY_PORT=8767
```

Do not bind the authority server directly to a public/non-loopback interface. The implementation rejects such configuration.

## Separated-host transport

When NetworkCore and Legit Assistant do not share a network namespace:

```text
Assistant --HTTPS--> internal TLS service edge on network host --loopback--> 127.0.0.1:8767
```

The TLS edge must:

- expose only the two `/internal/assistant/v1/authority/*` POST paths;
- be network-restricted to the Assistant service;
- preserve `Authorization`, `X-Request-Id`, and `X-Request-Signature` unchanged;
- impose conservative request/response/time limits;
- never log bearer tokens, request signatures, or HMAC secrets.

The Assistant rejects non-loopback plaintext HTTP URLs, so a cross-host URL must use HTTPS.

The Kubernetes base deployment optionally imports a Secret named `legit-assistant-g12-runtime`. If the Secret is absent, the pod still starts and the G1.2 runtime remains fail-closed. The example manifest is not part of `kustomization.yaml`.

The base NetworkPolicy intentionally does not grant arbitrary Internet egress. If inference is external, prefer an internal TLS inference/egress gateway in an already-authorized namespace rather than opening unrestricted outbound traffic.

## Shared-secret rule

`LEGIT_ASSISTANT_NETWORKCORE_AUTHORITY_HMAC_KEY_B64` is a dedicated secret for the Assistant -> NetworkCore authority direction. Do not reuse the Panel bridge secret or the existing NetworkCore -> Assistant Minecraft ingress secret.

Use at least 32 random bytes before Base64 encoding.

## Fail-closed expectations

- no PostgreSQL config -> G1.2 production composition is not installed;
- no/invalid authority config -> actor/security authority is unavailable;
- no/invalid inference config -> inference is unavailable;
- missing channel/profile permission -> authorization denied before execution;
- NetworkCore outage -> no stale implicit allow;
- provider outage -> bounded unavailable response, no fake success.

## Acceptance order

1. Run the canonical Java 25 / Gradle 9.6.1 build/check gate.
2. Start NetworkCore and confirm the loopback authority listener is enabled.
3. Verify the TLS service edge from the Assistant host without exposing the loopback port directly.
4. Start Assistant with PostgreSQL, authority, and inference configuration.
5. Verify Panel positive and negative paths.
6. Verify Minecraft `@BuggyBot` positive and silent-deny paths.
7. Build G1.3 dedicated Discord BuggyBot.
8. Run the final Panel + Minecraft + Discord acceptance matrix.
