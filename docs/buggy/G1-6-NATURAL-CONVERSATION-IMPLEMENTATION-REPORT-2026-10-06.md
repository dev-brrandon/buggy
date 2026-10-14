# G1.6 Natural Conversation — Implementation Report

Date: 2026-10-06
Source version: `0.1.0-dev.21`
Runtime live baseline: `0.1.0-dev.18-first-live`
Status: **IMPLEMENTED / SOURCE-VERIFIED / NOT LIVE**

## 1. Scope

G1.6 adds Buggy's first native conversation core without an external model provider:

- bounded Conversation Context;
- deterministic Intent Engine;
- bounded Entity Resolver with recent-turn reference resolution;
- semantic Response Planner;
- Natural Response Composer using the immutable G1.4 personality baseline;
- native pipeline execution through the existing Foundation `RuntimeRequestPipeline`.

G1.6 deliberately does **not** implement G1.7 network/server/service knowledge or tools, G1.8 observation/presence, G1.9 autonomous learning/maturity application, G1.10 external-model routing, or G1.11+ write/self-modification tools.

## 2. Conversation Context

`BuggyConversationContext` carries only already-authorized descriptive context:

- actor identity reference;
- channel;
- conversation key;
- optional server reference;
- current message;
- detected language;
- at most 24 recent turns;
- at most 16 bounded metadata entries.

Context never grants permissions or capabilities. Existing Foundation/NetworkCore authorization remains authoritative.

The persisted Panel conversation path supplies recent Foundation conversation messages after classification/redaction filtering. Direct channel messages remain subject to the same runtime gates and receive the bounded transport context available at that ingress.

## 3. Intent Engine

The G1.6 classifier is deterministic and framework-free. Initial intents are:

- `GREETING`;
- `FAREWELL`;
- `THANKS`;
- `IDENTITY_QUERY`;
- `CAPABILITY_QUERY`;
- `SOCIAL_STATUS_QUERY`;
- `CONTINUATION`;
- `ACTION_REQUEST`;
- `INFORMATION_QUERY`;
- `STATEMENT`.

Intent classification is descriptive only. It does not authorize execution and does not infer NetworkCore permissions.

## 4. Entity Resolver

The initial symbolic resolver understands bounded native concepts:

- `BUGGY_SELF`;
- `PRIMARY_OWNER` as a concept only, never owner-secret projection;
- `NETWORK`;
- `SERVER`;
- `SERVICE`;
- `MEMORY`;
- `ROADMAP`.

For short references such as `das`, `damit`, `weiter`, `that` or `it`, the resolver may inspect at most four recent turns. It accumulates at most one entity per entity type and never expands authorization scope.

## 5. Response Plan

The planner converts intent/entity results into semantic response acts before prose is produced. Important fail-safe behavior:

- live network/server/service questions plan `EXPLAIN_G17_KNOWLEDGE_LIMIT`;
- operational imperatives plan `EXPLAIN_G17_ACTION_LIMIT`;
- no G1.6 response plan executes a tool;
- no G1.6 response plan mutates state;
- unsupported questions ask for a clearer referent instead of fabricating an answer.

This keeps G1.7 and later blocks structurally separate.

## 6. Native Buggy voice

`BuggyNaturalResponseComposer` uses the immutable G1.4 Native Personality baseline to select the initial deterministic voice. The current baseline yields a friendly, direct style while preserving the Core Protocol principle `do not fabricate certainty`.

German and English are supported by the initial deterministic language detector/composer. No model/API call is required.

## 7. Runtime integration

`BuggyNativeConversationPipelineExecution` is now the production source-candidate execution path for:

- `assistant.conversation.message.append`;
- `assistant.buggy.chat.respond`.

Both paths still pass through the canonical Foundation runtime gates before native conversation execution.

The older `BuggyInferencePipelineExecution` remains in source for G1.2 regression compatibility, but the G1.6 production bootstrap no longer wires `OpenAiCompatibleInferencePort` or delegates Buggy chat responses to external inference. External model routing remains reserved for G1.10.

## 8. Persistence

G1.6 adds no database schema and no migration. It consumes the existing Foundation conversation history and the already implemented G1.4/G1.5 state. `V100_007` remains the latest migration.

The immutable G1.4 Birth/Native-Core baseline is unchanged. No automatic personality, maturity, memory, or capability revision is written merely by generating a G1.6 response.

## 9. Security boundaries

G1.6 preserves the established boundaries:

- NetworkCore remains security authority;
- channel metadata cannot grant authority;
- conversation context is non-authoritative;
- model providers are not part of the production G1.6 path;
- live state is not fabricated;
- operational actions are not executed;
- owner memory is not projected merely because a caller can converse with Buggy.

## 10. Verification

The dedicated `G16BuggyNaturalConversationSelfTest` verifies:

- bounded 24-turn context;
- German/English language selection;
- greeting, identity, capability, continuation and action intents;
- contextual entity resolution across bounded recent turns;
- explicit G1.7 knowledge/action boundaries;
- native pipeline output and classification preservation;
- production source wiring with no external inference provider.

The complete Buggy and bootstrap regression suite passes, including B12, G1.0, G1.2, G1.4, G1.5 and G1.6.

## 11. Deployment

No live deployment was performed. `services-01` remains on `0.1.0-dev.18-first-live` until a separate deployment gate is explicitly executed.

## 12. Next block

`G1.7 — Native Knowledge & Read-Only Tools`

Planned scope remains:

- NetworkCore/network/server/service knowledge;
- incident/status data;
- first deterministic diagnosis rules;
- read-only tool registry and tools;
- no destructive actions.
