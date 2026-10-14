# G1.6 Change Manifest

Source version: `0.1.0-dev.21`
Date: 2026-10-06

## Added — conversation domain

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationLanguage.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationRole.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationTurn.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationContext.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationContextBuilder.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationIntent.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyIntentClassification.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyIntentEngine.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationEntityType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationEntity.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyEntityResolution.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyEntityResolver.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyResponseAct.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyResponsePlan.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyResponsePlanner.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyNaturalResponseComposer.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyNaturalConversationResult.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyNaturalConversationEngine.java`

Package-private normalization helper:

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/conversation/BuggyConversationText.java`

## Added — runtime

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/runtime/g1/BuggyNativeConversationPipelineExecution.java`

## Added — verification

- `assistant-buggy/src/selfTest/java/de/legitserver/assistant/buggy/G16BuggyNaturalConversationSelfTest.java`
- Gradle task `g16NaturalConversationSelfTest`

## Modified — production composition

- `assistant-bootstrap/src/main/java/de/legitserver/assistant/bootstrap/g12/G12RuntimeConfiguration.java`
  - production Buggy chat route now uses native G1.6 execution;
  - persisted Panel history is mapped to typed conversation turns;
  - external inference provider is no longer wired into the production G1.6 conversation route.

## Modified — version/status documentation

- root `build.gradle`: `0.1.0-dev.21`
- `README.md`
- `docs/buggy/G1-STATUS-2026-10-06.md`
- `docs/buggy/BUGGY-NATIVE-CORE-8-BLOCK-ROADMAP-2026-10-06.md`

## Explicitly unchanged

- G1.4 immutable Birth/Native-Core baseline;
- G1.5 `V100_007` schema and memory/development persistence;
- NetworkCore authority model;
- Foundation request/pipeline/security boundaries;
- no G1.7 live knowledge/read-only tools;
- no G1.8 observation/presence modes;
- no G1.9 automatic personality/maturity application;
- no G1.10 external model gateway activation;
- no G1.11+ write/self-modification authority.
