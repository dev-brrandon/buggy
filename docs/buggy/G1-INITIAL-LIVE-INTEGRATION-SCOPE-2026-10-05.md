# G1 — Initial Buggy Live Integration Scope

## Goal

Bring the single Buggy runtime to the three initial user-facing channels and then run one shared acceptance pass.

```text
                 Website / Panel
                       |
Minecraft / NetworkCore -- Legit Assistant Runtime -- dedicated Discord BuggyBot
                       |
                  assistant:buggy
```

There is one Buggy runtime. Website, Minecraft, and Discord are channel adapters, not separate assistants.

## G1.0 Shared Assistant Entry Layer

Normalize inbound channel messages into one server-side assistant request contract containing at minimum:

- assistant identity;
- canonical Legit user identity when resolved;
- channel type and channel identity;
- conversation/session reference;
- message;
- bounded channel context;
- timestamp/correlation metadata.

Identity federation may establish that Panel, Minecraft, and Discord identities belong to the same Legit user, but it must never transfer authority between channels. Every request remains independently authorized.

## G1.1 Website / Panel

Initial website scope:

- Buggy chat;
- session/conversation continuity;
- Buggy identity/status/personality projection;
- minimal activity/degraded/error state;
- server-side Panel Backend Bridge to the Assistant API/Pipeline.

The browser must not call the Assistant API directly. B8/B9 trust-boundary rules remain authoritative.

## G1.1.5 Delegated Owner Access

Buggy access is not inherited from generic staff rank or global break-glass authority.

- `typixbrandy` is the immutable PRIMARY_OWNER.
- Delegates require one explicit Buggy profile (`basic`, `operations`, `development`) plus an explicit channel grant.
- `DENY` wins.
- Only the PRIMARY_OWNER administers Buggy profiles/channels and protected Buggy permission nodes.

## G1.2 Minecraft / NetworkCore + Runtime Completion

Buggy is addressed in normal Minecraft chat by mentioning:

```text
@BuggyBot <message>
```

There is no `/buggy` command in the initial design.

For users without a valid Buggy profile/channel grant, the mention remains silently unhandled and must not create Assistant runtime work.

NetworkCore is the Minecraft channel/security boundary. The Assistant independently re-resolves actor identity and current Buggy-safe security projection from NetworkCore before canonical pipeline execution.

Panel and Minecraft must converge on the same `RuntimeRequestPipeline` and provider-neutral `InferencePort`. No channel may directly bind itself to an LLM/provider adapter.

## G1.3 Discord — dedicated Buggy bot

Buggy is represented by its **own official Discord Application and Bot User**. This is not the existing/general LEGIT Discord bot.

Initial interaction is mention-based:

```text
@BuggyBot <message>
```

The dedicated Discord service is responsible for Discord event transport, bounded guild/channel/message context, identity-link transport, rate limiting, reply/thread transport, and failure handling. It must not derive Buggy authority from Discord roles.

Canonical path:

```text
Discord BuggyBot -> NetworkCore identity/authority -> Legit Assistant -> same Buggy runtime
```

A normal automated Discord user/self-bot is explicitly out of scope.

## G1 acceptance order

1. Complete G1.2 runtime source and deployment boundary.
2. Build G1.3 dedicated Discord BuggyBot.
3. Run Panel + Minecraft + Discord end-to-end against the same runtime.
4. Include positive and negative authorization/failure-path tests.

## Excluded from initial G1

- BYTE;
- KAESE;
- broad production automation rollout;
- full activation of all B5 tool handlers;
- automatic expansion to Mail or other channels;
- cross-channel permission inheritance.
