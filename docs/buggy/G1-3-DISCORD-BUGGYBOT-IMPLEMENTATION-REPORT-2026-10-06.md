# G1.3 Dedicated Discord BuggyBot — Implementation Report

Canonical date: 2026-10-06

## Result

**SOURCE IMPLEMENTED / OFFLINE VERIFIED / LIVE ACCEPTANCE PENDING.**

G1.3 adds a completely independent Discord Application/Bot service for Buggy. It does not reuse or modify the general LEGIT Discord bot. The next gate is the planned shared live acceptance of Panel + Minecraft + Discord.

## Canonical path

```text
Discord @BuggyBot mention
  -> dedicated legit-buggy-discord service
  -> NetworkCore Discord Integration API
  -> Discord external identity link
  -> BuggyDelegatedAccessEvaluator(Channel.DISCORD)
  -> NetworkCore -> Assistant private ingress
  -> BuggyChannelIngress(DISCORD)
  -> FoundationBuggyChannelExecution
  -> RuntimeRequestPipeline
  -> Assistant -> NetworkCore actor/security authority re-resolution
  -> same provider-neutral InferencePort used by Panel/Minecraft
  -> reply through the dedicated Discord bot
```

The Discord service contains no permission authority and no inference/provider access.

## Dedicated Discord service

New standalone project: `legit-buggy-discord`, version `0.1.0-dev.1`.

Properties:

- JDK 25 Gradle target;
- dependency-free Java implementation using JDK HTTP/WebSocket APIs;
- Discord Gateway v10;
- only `GUILDS` + `GUILD_MESSAGES` intents;
- mention-only processing;
- explicit allowed-guild list, optional allowed-channel list;
- bot/self-message rejection;
- bounded local concurrency and per-user cooldown;
- bounded Discord output chunking;
- `allowed_mentions.parse=[]` on responses;
- conservative Discord 429 retry;
- session resume, reconnect and heartbeat handling;
- loopback-only health endpoint;
- no token/API-key logging.

## Bot -> NetworkCore security

`POST /api/discord/buggy/messages`

Requires both:

1. existing Discord Integration `X-API-Key`;
2. a dedicated Buggy Discord HMAC key.

The HMAC binds:

- HTTP method;
- path;
- timestamp;
- nonce;
- request ID;
- SHA-256 request-body digest.

NetworkCore enforces bounded clock skew and nonce replay protection. Missing/invalid HMAC material disables the Buggy endpoint rather than weakening authentication.

## Identity + authorization

NetworkCore resolves the incoming Discord user through the canonical `discord` external-identity link. The linked Minecraft UUID is passed into `BuggyAccessResolver` with `BuggyAccessPolicy.Channel.DISCORD`.

Therefore:

- PRIMARY_OWNER remains the immutable Buggy owner;
- other users require an explicit Buggy delegate profile plus `buggy-channel-discord`;
- generic ranks, Discord roles and global NetworkCore break-glass do not imply Buggy access;
- denied or unlinked users receive no Discord reply.

## Second authority verification

The Assistant does not trust the UUID forwarded by the Discord bridge by itself.

For DISCORD-origin requests, `HttpNetworkCoreAuthorityClient` includes the presented Discord ID in actor resolution. NetworkCore re-reads the `discord` external-identity link and requires the linked Minecraft UUID to equal the presented actor UUID before returning the canonical actor.

The normal current security-snapshot projection and channel/capability checks then run inside the canonical Assistant pipeline.

## Conversation isolation

Discord conversation keys are scoped as:

`discord:<guildId>:<channelId>:<discordUserId>`

This prevents unrelated users in the same channel from sharing Buggy conversation state.

## Live data still required

Not required for source implementation, but required for the next live acceptance:

- dedicated Discord Application/Bot token;
- target Guild ID and optional allowed Channel IDs;
- Bot invited to the server with minimal channel permissions;
- production NetworkCore service-edge URL/API key;
- dedicated Bot -> NetworkCore HMAC secret installed on both sides;
- G1.2 Assistant runtime/inference live configuration.

## Version policy

Existing component release numbers are deliberately not bumped before the canonical Java 25 / Gradle gate and triple-channel live acceptance:

- Assistant remains `0.1.0-dev.18`;
- NetworkCore remains NC69;
- System remains SYS68;
- LegitPlatform remains dev.8 baseline;
- Panel remains Gateway dev.22 / Web dev.15.

The new standalone Discord adapter starts at `0.1.0-dev.1`.
