# G1.2 Buggy Runtime Completion — Implementation Report

Canonical date: 2026-10-05

## Result

**SOURCE IMPLEMENTED / OFFLINE VERIFIED.**

This pass removes the intentional `BUGGY_RUNTIME_NOT_CONFIGURED` gap when the required production dependencies are configured. It does not claim live acceptance: the canonical Java 25 / Gradle 9.6.1 gate and real infrastructure E2E remain pending.

## Baseline retained

- Legit Assistant: `0.1.0-dev.18` (Foundation compatibility baseline remains Phase 0–18 accepted)
- NetworkCore: `0.18.0-dev.23.5-hotfix.69` (NC69)
- System: `1.1.0-dev.23.5-hotfix.68` (SYS68)
- LegitPlatform: `0.1.2-dev.8`, API `1.6.0`, Bridge Protocol `1`
- Panel Gateway: `0.1.0-dev.22`
- Panel Web: `0.1.0-dev.15`

No release-number bump is performed before the canonical Java 25/Gradle and live acceptance gates.

## Implemented runtime path

```text
Panel
  -> PipelineAssistantApiBackend
  -> RuntimeRequestPipeline

Minecraft
  -> NetworkCore @BuggyBot transport
  -> private HMAC Assistant ingress
  -> BuggyChannelIngress(MINECRAFT)
  -> FoundationBuggyChannelExecution
  -> RuntimeRequestPipeline

RuntimeRequestPipeline
  -> service authentication
  -> authoritative actor re-resolution
  -> current NetworkCore security snapshot
  -> assistant use + channel gate
  -> conversation ownership (where applicable)
  -> capability/resource policy
  -> rate/admission/idempotency controls
  -> BuggyInferencePipelineExecution
  -> provider-neutral InferencePort
  -> response security / formatting / audit
```

## New production composition

`assistant-bootstrap/.../g12/G12RuntimeConfiguration.java` composes the previously framework-free Foundation pieces into the Spring production host only when Assistant PostgreSQL is configured.

Missing NetworkCore authority or inference configuration remains fail-closed at request time.

## NetworkCore authority re-resolution

A dedicated read-only authority transport was added to NetworkCore:

- `POST /internal/assistant/v1/authority/actor`
- `POST /internal/assistant/v1/authority/snapshot`

Properties:

- loopback bind only;
- dedicated 256-bit+ HMAC secret;
- service/audience/scope binding;
- request-ID binding;
- replay protection;
- bounded request body;
- detached request signature binding `method + path + requestId + SHA-256(body)`;
- `Cache-Control: no-store`;
- canonical LegitPlatform-backed security snapshot source.

For separated hosts, the loopback endpoint must stay private and be exposed only through an authenticated/internal TLS reverse proxy or equivalent service edge. Direct non-loopback plaintext HTTP is rejected by the Assistant client.

## Buggy permission projection

The authority endpoint does not pass generic effective Buggy permissions through unchanged.

1. All generic Buggy permission nodes are stripped.
2. `typixbrandy` receives the immutable PRIMARY_OWNER projection.
3. Every other actor is re-evaluated with `BuggyDelegatedAccessEvaluator` against the explicit user profile and current PermissionFoundation snapshot.
4. Only explicitly allowed profile/channel permissions are projected back to the Assistant.

This prevents normal rank inheritance or global NetworkCore break-glass from manufacturing Buggy access.

## Canonical channel authorization

The Assistant early authorization stage now checks the channel gate as well as `networkcore.assistant.use.buggy`:

- PANEL -> `networkcore.assistant.buggy.channel.panel`
- MINECRAFT -> `networkcore.assistant.buggy.channel.minecraft`
- DISCORD -> `networkcore.assistant.buggy.channel.discord`

Discord is included in the catalog now so G1.3 can use the same runtime without weakening the policy boundary.

## Inference adapter

A bounded OpenAI-compatible `InferencePort` implementation was added. It is provider-neutral at the Assistant contract boundary.

Security/availability properties:

- HTTPS required for non-loopback endpoints;
- external inference disabled unless `LEGIT_ASSISTANT_INFERENCE_ALLOW_EXTERNAL=true`;
- API key optional for local compatible providers, secret for external providers;
- bounded request and response sizes;
- bounded generated output request;
- request-ID propagation;
- conversation-history `CONTEXT` is serialized as unprivileged provider `user` content, never as `system`;
- 429/5xx -> unavailable;
- non-success provider rejection -> bounded rejection;
- absent/invalid configuration -> fail-closed NoOp inference.

This G1.2 route is intentionally read-only natural-language execution. It must not claim that state-changing actions were performed without a separately authorized tool path.

## Panel protection

Panel conversation reads are re-authorized against NetworkCore and require:

- canonical actor binding;
- current revision;
- Buggy use permission;
- Panel channel permission;
- read capability;
- conversation owner match;
- PANEL origin;
- Buggy assistant identity.

The previous risk of resolving an arbitrary known conversation ID is therefore closed in the G1.2 read port.

## Resource policy

The G1.2 runtime does not use an unconditional resource allow. The production composition explicitly allows only the read capability on `conversation` and `buggy-conversation` resource types; unknown types remain denied.

## Verification in this environment

Passed against the final working source:

- Assistant main source: 648 Java files, Java 21 `-Xlint:all,-processing -Werror`
- Assistant self-tests source: 48 Java files, Java 21 `-Xlint:all,-processing -Werror`
- `G1_2_BUGGY_RUNTIME_COMPLETION_SELF_TEST: PASS`
- `G1_2_NETWORKCORE_AUTHORITY_CLIENT_SELF_TEST: PASS`
- `G1_2_INFERENCE_ADAPTER_SELF_TEST: PASS`
- `PHASE13_RUNTIME_PIPELINE_SELF_TEST: PASS`
- `PHASE3_NETWORKCORE_ADAPTER_SELF_TEST: PASS`
- `PHASE15_PANEL_PIPELINE_SELF_TEST: PASS`
- `G1_2_NETWORKCORE_INGRESS_SECURITY_SELF_TEST: PASS`
- `G1_2_ASSISTANT_AUTHORITY_SHAPE_SELF_TEST: PASS`
- `G1_BUGGY_DELEGATED_ACCESS_SELF_TEST: PASS`
- standalone changed NetworkCore authority server compile under Java 21 `-Xlint:all -Werror`: PASS

### Not claimed

The source requires Java 25 and Gradle 9.6.1 for the canonical build gate. This execution environment only exposes Java 21 and does not have the Gradle 9.6.1 wrapper distribution cached; internet access is unavailable. Therefore **no Java-25/Gradle PASS is claimed here**.

## Live prerequisites before Panel/Minecraft acceptance

- Assistant PostgreSQL schema/migrations present and reachable;
- Buggy identity runtime initialized;
- NetworkCore authority endpoint enabled with shared dedicated HMAC secret;
- TLS service edge from Assistant to the loopback NetworkCore authority endpoint when services are separated;
- real OpenAI-compatible inference endpoint/model configured;
- external provider opt-in only when intended;
- existing Panel HMAC bridge secrets present;
- existing NetworkCore -> Assistant Minecraft ingress secret/URL present.

The final three-channel live acceptance is intentionally scheduled after G1.3 so Panel, Minecraft, and the dedicated Discord BuggyBot can be validated against the same runtime in one pass.
