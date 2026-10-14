# B6 Change Manifest

## New Buggy production source

### Tasks
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tasks/BuggyTaskId.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tasks/BuggyTaskStatus.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tasks/BuggyTaskRevision.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tasks/BuggyTaskRelation.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tasks/BuggyTaskRelationType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/service/BuggyTaskService.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/repository/BuggyTaskRepository.java`

### Activity
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/activity/BuggyAttentionLevel.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/activity/BuggyActivityType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/activity/BuggyActivityEntry.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/activity/BuggyActivityBundle.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/activity/BuggyActivityBundler.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/repository/BuggyActivityRepository.java`

### Automation
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationLevel.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationTriggerType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggySignalType.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationState.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationSignal.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationRevision.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationRun.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationActivationGuard.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationExecutionPort.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/automation/BuggyAutomationExecutionResult.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/repository/BuggyAutomationRepository.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/service/BuggyAutomationService.java`

## New integrations

- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/FoundationRequestAutomationActivationGuard.java`
- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/JdbcBuggyTaskRepository.java`
- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/JdbcBuggyActivityRepository.java`
- `assistant-integrations/src/main/java/de/legitserver/assistant/integrations/buggy/JdbcBuggyAutomationRepository.java`

## New verification

- `assistant-buggy/src/selfTest/java/de/legitserver/assistant/buggy/B6BuggyTasksActivityAutomationSelfTest.java`
- `assistant-integrations/src/selfTest/java/de/legitserver/assistant/integrations/buggy/B6AutomationActivationGuardSelfTest.java`
- `assistant-integrations/src/postgresTest/java/de/legitserver/assistant/integrations/buggy/B6BuggyTasksAutomationPostgresSelfTest.java`

## Persistence

- `assistant-persistence/src/main/resources/db/migration/V100_004__create_buggy_tasks_activity_automation.sql`

## Modified

- `assistant-buggy/build.gradle`
- `assistant-integrations/build.gradle`
- `assistant-observability/src/main/java/de/legitserver/assistant/observability/audit/AuditAction.java` (additive `BUGGY_AUTOMATION` audit category only)
- `README.md`
- `docs/buggy/CURRENT-TRACK.txt`

## Authority statement

No new Security, Request/Approval, Tool Runtime, Audit, Pipeline or Supervisor authority is introduced. B6 reuses those Foundation systems.
