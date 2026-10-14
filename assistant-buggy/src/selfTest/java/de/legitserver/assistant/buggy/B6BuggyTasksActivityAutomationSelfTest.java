package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.activity.BuggyActivityBundler;
import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationExecutionResult;
import de.legitserver.assistant.buggy.automation.BuggyAutomationLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRun;
import de.legitserver.assistant.buggy.automation.BuggyAutomationSignal;
import de.legitserver.assistant.buggy.automation.BuggyAutomationState;
import de.legitserver.assistant.buggy.automation.BuggyAutomationTriggerType;
import de.legitserver.assistant.buggy.automation.BuggySignalType;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyAutomationRepository;
import de.legitserver.assistant.buggy.repository.BuggyTaskRepository;
import de.legitserver.assistant.buggy.service.BuggyAutomationService;
import de.legitserver.assistant.buggy.service.BuggyTaskService;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelation;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelationType;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingBudgetTracker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class B6BuggyTasksActivityAutomationSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T22:45:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b6-owner");
    private B6BuggyTasksActivityAutomationSelfTest() {}

    public static void main(String[] args) {
        MemoryStore store = new MemoryStore();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BuggyTaskService tasks = new BuggyTaskService(store, store, clock);
        BuggyTaskRevision parent = tasks.create("Investigate PostgreSQL", "Find the health regression.", BuggyAttentionLevel.NOTICE, OWNER);
        BuggyTaskRevision child = tasks.create("Compare DB metrics", "Compare baseline and current metrics.", BuggyAttentionLevel.INFO, OWNER);
        tasks.relate(parent.taskId(), child.taskId(), BuggyTaskRelationType.PARENT_CHILD, OWNER);
        expectFailure(() -> tasks.relate(child.taskId(), parent.taskId(), BuggyTaskRelationType.PARENT_CHILD, OWNER));
        tasks.transition(parent.taskId(), BuggyTaskStatus.ACTIVE, 25, null, BuggyAttentionLevel.NOTICE, OWNER);
        tasks.transition(parent.taskId(), BuggyTaskStatus.WAITING_APPROVAL, 75, "Need Owner decision", BuggyAttentionLevel.ACTION_REQUIRED, OWNER);
        require(store.history(parent.taskId()).size() == 3, "task revisions must be append-only");
        require(store.relations(parent.taskId()).size() == 1, "task relation must persist");
        expectFailure(() -> tasks.transition(parent.taskId(), BuggyTaskStatus.OPEN, 10, null, BuggyAttentionLevel.INFO, OWNER));

        BuggyActivityEntry sanitized = new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.OBSERVATION_RECEIVED,
                BuggyAttentionLevel.INFO, parent.taskId(), null, "Credential signal", Map.of("password", "hunter2"), OWNER, NOW);
        require("[REDACTED]".equals(sanitized.details().get("password")), "activity must sanitize secret values");
        BuggyActivityEntry bundledSecond = new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.OBSERVATION_RECEIVED,
                BuggyAttentionLevel.NOTICE, parent.taskId(), null, "Credential signal follow-up", Map.of("status", "same"), OWNER,
                NOW.plusSeconds(30));
        require(new BuggyActivityBundler(Duration.ofMinutes(5)).bundle(List.of(sanitized, bundledSecond)).getFirst().count() == 2,
                "low-value activity should be bundled");

        ArrayList<BuggyAutomationLevel> executed = new ArrayList<>();
        BuggyAutomationService automation = new BuggyAutomationService(store, store, a -> a.activationRequestId() != null,
                (a, signal, level) -> { executed.add(level); return BuggyAutomationExecutionResult.success("OK"); },
                new SelfHealingBudgetTracker(), new SlidingWindowRateLimiter(20, Duration.ofMinutes(1), 128, clock),
                new AuditRecorder(record -> AuditAppendResult.recordedResult()), clock);
        BuggyAutomationRevision low = policy("auto:postgres-health", ToolRiskLevel.LOW, BuggyAutomationLevel.ACT, null);
        automation.save(0, low);
        List<BuggyAutomationRun> runs = automation.observe(new BuggyAutomationSignal(
                BuggySignalType.POSTGRESQL_HEALTH, "postgres-primary", false, Map.of("status", "degraded"), actor(), NOW));
        require(runs.size() == 1 && runs.getFirst().finalLevel() == BuggyAutomationLevel.ACT, "LOW automation may act");
        require(executed.contains(BuggyAutomationLevel.ACT), "execution port must receive ACT");
        require(automation.observe(new BuggyAutomationSignal(
                BuggySignalType.POSTGRESQL_HEALTH, "postgres-primary", true, Map.of("status", "healthy"), actor(), NOW)).isEmpty(),
                "healthy state must not create noisy actions");

        expectFailure(() -> policy("auto:illegal-high-act", ToolRiskLevel.HIGH, BuggyAutomationLevel.ACT,
                new ApprovalRequestId(UUID.randomUUID())));
        BuggyAutomationRevision privileged = policy("auto:source-analysis", ToolRiskLevel.HIGH, BuggyAutomationLevel.PREPARE,
                new ApprovalRequestId(UUID.randomUUID()));
        automation.save(0, privileged);
        require(store.recent(100).stream().anyMatch(e -> e.automationId() != null), "activity stream must expose automation work");
        require(store.runs.size() == 1, "suppressed healthy observation must not produce a run");

        MemoryStore failClosedStore = new MemoryStore();
        BuggyAutomationService failClosed = new BuggyAutomationService(failClosedStore, failClosedStore, a -> true,
                (a, signal, level) -> BuggyAutomationExecutionResult.success("PREPARED"),
                new SelfHealingBudgetTracker(), new SlidingWindowRateLimiter(20, Duration.ofMinutes(1), 128, clock),
                new AuditRecorder(record -> AuditAppendResult.unavailable()), clock);
        failClosed.save(0, privileged);
        expectAuditFailure(() -> failClosed.observe(new BuggyAutomationSignal(
                BuggySignalType.RECURRING_BUG, "source-module", false, Map.of("issue", "repeat"), actor(), NOW)));
        System.out.println("B6_BUGGY_TASKS_ACTIVITY_AUTOMATION_SELF_TEST: PASS");
    }

    private static ActorContext actor() {
        return new ActorContext(OWNER, ActorType.HUMAN, Origin.PANEL, "network:buggy-b6-owner", null, "panel-b6", null,
                new RequestId("req-b6-automation"), new TraceId("44444444444444444444444444444444"), "assistant-service", 1, NOW);
    }

    private static BuggyAutomationRevision policy(String id, ToolRiskLevel risk, BuggyAutomationLevel level, ApprovalRequestId approval) {
        ToolId tool = risk == ToolRiskLevel.LOW ? new ToolId("tool.diagnostics.service.health") : new ToolId("tool.source.read");
        return new BuggyAutomationRevision(id, 1, null, id, BuggyAutomationState.ENABLED,
                BuggyAutomationTriggerType.SIGNAL,
                risk == ToolRiskLevel.LOW ? BuggySignalType.POSTGRESQL_HEALTH : BuggySignalType.RECURRING_BUG,
                null, level, BuggyAttentionLevel.NOTICE, tool,
                risk == ToolRiskLevel.LOW ? BuggySecurityCapabilities.DIAGNOSTICS_READ : BuggySecurityCapabilities.SOURCE_READ,
                risk, 3, Duration.ofSeconds(30), Duration.ofMinutes(10), 10, Duration.ofMinutes(2), 3,
                approval, OWNER, NOW);
    }

    private static void expectFailure(Runnable operation) {
        try { operation.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected operation failure");
    }

    private static void expectAuditFailure(Runnable operation) {
        try { operation.run(); } catch (AuditUnavailableException expected) { return; }
        throw new AssertionError("Expected fail-closed audit failure");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class MemoryStore implements BuggyTaskRepository, BuggyActivityRepository, BuggyAutomationRepository {
        private final Map<BuggyTaskId, List<BuggyTaskRevision>> tasks = new HashMap<>();
        private final List<BuggyTaskRelation> relations = new ArrayList<>();
        private final List<BuggyActivityEntry> activities = new ArrayList<>();
        private final Map<String, List<BuggyAutomationRevision>> automations = new HashMap<>();
        private final List<BuggyAutomationRun> runs = new ArrayList<>();
        @Override public Optional<BuggyTaskRevision> findCurrent(BuggyTaskId id) { var h=tasks.get(id); return h==null||h.isEmpty()?Optional.empty():Optional.of(h.getLast()); }
        @Override public List<BuggyTaskRevision> history(BuggyTaskId id) { return List.copyOf(tasks.getOrDefault(id, List.of())); }
        @Override public BuggyTaskRevision append(long expected, BuggyTaskRevision r) { long actual=findCurrent(r.taskId()).map(BuggyTaskRevision::revision).orElse(0L); if(actual!=expected) throw new IllegalStateException("stale"); tasks.computeIfAbsent(r.taskId(), k->new ArrayList<>()).add(r); return r; }
        @Override public void addRelation(BuggyTaskRelation r) { relations.add(r); }
        @Override public List<BuggyTaskRelation> relations(BuggyTaskId id) { return relations.stream().filter(r->r.sourceTaskId().equals(id)||r.targetTaskId().equals(id)).toList(); }
        @Override public void append(BuggyActivityEntry e) { activities.add(e); }
        @Override public List<BuggyActivityEntry> recent(int limit) { return activities.reversed().stream().limit(limit).toList(); }
        @Override public Optional<BuggyAutomationRevision> findCurrent(String id) { var h=automations.get(id); return h==null||h.isEmpty()?Optional.empty():Optional.of(h.getLast()); }
        @Override public List<BuggyAutomationRevision> history(String id) { return List.copyOf(automations.getOrDefault(id,List.of())); }
        @Override public BuggyAutomationRevision append(long expected, BuggyAutomationRevision r) { long actual=findCurrent(r.automationId()).map(BuggyAutomationRevision::revision).orElse(0L); if(actual!=expected) throw new IllegalStateException("stale"); automations.computeIfAbsent(r.automationId(), k->new ArrayList<>()).add(r); return r; }
        @Override public List<BuggyAutomationRevision> enabledForSignal(BuggySignalType t) { return automations.values().stream().map(List::getLast).filter(a->a.state()==BuggyAutomationState.ENABLED&&a.signalType()==t).toList(); }
        @Override public List<BuggyAutomationRevision> dueScheduled(Instant at) { return List.of(); }
        @Override public void appendRun(BuggyAutomationRun run) { runs.add(run); }
        @Override public Optional<BuggyAutomationRun> latestRun(String id) { return runs.reversed().stream().filter(r->r.automationId().equals(id)).findFirst(); }
    }
}
