# G1.0 — Shared Channel Entry Layer — Implementation Report

Date: 2026-10-05
Status: COMPLETE / SOURCE-VERIFIED
Baseline: G0 Runtime Accepted

## Scope

G1.0 establishes one authority-neutral inbound message envelope for the initial Buggy live channels:

- WEB / Panel
- MINECRAFT / NetworkCore
- DISCORD / official BuggyBot application bot

No channel adapter is allowed to carry permissions, roles, capability grants or approval authority in the envelope.

## Implemented contracts

- `BuggyChannelType`
- `BuggyChannelPrincipal`
- `BuggyChannelContext`
- `BuggyInboundMessage`
- `BuggyChannelIngress`

`BuggyChannelType` maps server-side trusted channels to the existing Foundation `Origin` enum.

## Security properties

- `assistantId` is fixed to `assistant:buggy`.
- actor/network/channel identities are bounded value objects.
- channel metadata is bounded and authority-neutral.
- message size is capped at 16 KiB UTF-8.
- clients cannot transport permission/capability state in the canonical envelope.
- inference/provider selection remains outside identity and outside the channel contract.

## Verification

`G1_0_BUGGY_CHANNEL_INGRESS_SELF_TEST: PASS`

The full Java 25 / Gradle 9.6.1 Assistant build and quality gate is also green after the change.
