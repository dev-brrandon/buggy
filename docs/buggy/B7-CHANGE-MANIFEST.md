# B7 Change Manifest

## New Buggy production source — Self-Development domain

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentId.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentState.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentChangeType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentEvidenceType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentApprovalStatus.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentContentPolicy.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentApprovalBinding.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentEvidence.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentPromotionPackage.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentApprovalPort.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/selfdev/BuggySelfDevelopmentRevision.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/repository/BuggySelfDevelopmentRepository.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/service/BuggySelfDevelopmentService.java`

## New B7 typed tool catalog

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggySelfDevelopmentToolDefinition.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggySelfDevelopmentToolCatalog.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggySelfDevelopmentToolRegistrar.java`

## New integrations

- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/FoundationSecretGuardedSelfDevelopmentContentPolicy.java`
- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/FoundationSelfDevelopmentApprovalGateway.java`
- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/JdbcBuggySelfDevelopmentRepository.java`

## New verification

- `assistant-buggy/src/selfTest/java/de/legitserver/assistant/buggy/B7BuggySelfDevelopmentLaboratorySelfTest.java`
- `assistant-integrations/src/selfTest/java/de/legitserver/assistant/integrations/buggy/B7SelfDevelopmentFoundationBindingSelfTest.java`
- `assistant-integrations/src/postgresTest/java/de/legitserver/assistant/integrations/buggy/B7BuggySelfDevelopmentPostgresSelfTest.java`
- `scripts/buggy-b7-postgres-test.sh`

## Persistence

- `assistant-persistence/src/main/resources/db/migration/V100_005__create_buggy_self_development.sql`

## Modified

- `assistant-buggy/build.gradle`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolSchemas.java`
- `assistant-integrations/build.gradle`
- `README.md`
- `docs/buggy/CURRENT-TRACK.txt`

## Authority statement

B7 introduces no new Security, Source Registry, Request/Approval, Tool Runtime, Audit, deployment, Pipeline or Supervisor authority. Self-Development preparation is autonomous only inside controlled typed boundaries; promotion and rollback remain governed by the accepted Foundation.
