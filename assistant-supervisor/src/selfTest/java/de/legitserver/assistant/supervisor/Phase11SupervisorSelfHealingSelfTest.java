package de.legitserver.assistant.supervisor;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.runtime.audit.ToolAudit;
import de.legitserver.assistant.runtime.execution.ToolOutputFilter;
import de.legitserver.assistant.runtime.execution.ToolRuntime;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolClassificationPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.runtime.schema.ToolFieldSchema;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.runtime.schema.ToolSchemaValidator;
import de.legitserver.assistant.runtime.schema.ToolValueType;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRiskLevel;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.secret.SecretGuard;
import de.legitserver.assistant.supervisor.health.CapabilityDegradationRegistry;
import de.legitserver.assistant.supervisor.health.InMemoryAssistantOperationalTelemetry;
import de.legitserver.assistant.supervisor.read.AssistantSupervisorReadService;
import de.legitserver.assistant.supervisor.read.SupervisorAccessDeniedException;
import de.legitserver.assistant.supervisor.read.SupervisorReadContext;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingBudgetTracker;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingEscalation;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingEscalationReceipt;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingPolicy;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingService;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingStatus;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingSuppressionReason;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Framework-free Phase-11 acceptance tests. */
public final class Phase11SupervisorSelfHealingSelfTest {
    private static final Instant START = Instant.parse("2026-10-04T10:00:00Z");
    private static final RequestId REQUEST_ID = new RequestId("req-phase11");
    private static final UUID ACTOR_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ToolSchema INPUT = ToolSchema.of(
            new ToolFieldSchema("action", ToolValueType.STRING, true, 64));
    private static final ToolSchema OUTPUT = ToolSchema.of(
            new ToolFieldSchema("result", ToolValueType.STRING, true, 64));

    private Phase11SupervisorSelfHealingSelfTest() {}

    public static void main(String[] args) {
        verifyCanonicalLowRiskAndRegistrationGuard();
        verifySupervisorCannotBypassSecurity();
        verifySupervisorHealthModel();
        verifySelfHealingSecurityAndSuccess();
        verifyBudgetsAndLoopSuppression();
        verifyFailureEscalationCreatesReportAndIncidentReference();
        System.out.println("PHASE11_SUPERVISOR_SELF_HEALING_SELF_TEST: PASS");
    }

    private static void verifyCanonicalLowRiskAndRegistrationGuard() {
        var rule = AssistantSecurityCatalog.rule(AssistantCapabilities.SELF_HEAL_LOW).orElseThrow();
        require(rule.riskLevel() == AuthorizationRiskLevel.LOW,
                "assistant.selfheal.low must be canonically LOW risk for Phase 11");

        ToolRegistry registry = new ToolRegistry();
        expectFailure(IllegalArgumentException.class, () -> registry.register(
                selfHealDescriptor("tool.selfheal.high", ToolRiskLevel.HIGH, ToolAuditPolicy.CRITICAL_REQUIRED),
                context -> new ToolOutput(Map.of("result", "x"), DataClassification.INTERNAL)));
        expectFailure(IllegalArgumentException.class, () -> registry.register(
                selfHealDescriptor("tool.selfheal.auditweak", ToolRiskLevel.LOW, ToolAuditPolicy.REQUIRED),
                context -> new ToolOutput(Map.of("result", "x"), DataClassification.INTERNAL)));
    }

    private static void verifySupervisorCannotBypassSecurity() {
        MutableClock clock = new MutableClock(START);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        telemetry.recordRequest(AssistantId.BUGGY, Duration.ofMillis(12), true);
        DependencyHealthRegistry dependencies = readyDependencies(clock);
        CapabilityDegradationRegistry degraded = new CapabilityDegradationRegistry();
        RecordingAuditPort audits = new RecordingAuditPort();
        SupervisorReadContext context = readContext(clock);

        AssistantSupervisorReadService denied = new AssistantSupervisorReadService(
                (ctx, target) -> AuthorizationDecision.deny(
                        AuthorizationDenialReason.PERMISSION_DENIED, 1, AuthorizationRiskLevel.HIGH),
                telemetry, dependencies, degraded, new AuditRecorder(audits), clock);
        expectFailure(SupervisorAccessDeniedException.class,
                () -> denied.getAssistantMetrics(context, AssistantId.BUGGY));
        require(audits.records.stream().anyMatch(record -> record.outcome().name().equals("DENIED")),
                "denied supervisor read must be audited");

        AssistantSupervisorReadService allowed = new AssistantSupervisorReadService(
                (ctx, target) -> AuthorizationDecision.allow(2, AuthorizationRiskLevel.HIGH),
                telemetry, dependencies, degraded, new AuditRecorder(audits), clock);
        require(allowed.getAssistantMetrics(context, AssistantId.BUGGY).requests() == 1,
                "authorized supervisor read returns metrics");

        AssistantSupervisorReadService auditUnavailable = new AssistantSupervisorReadService(
                (ctx, target) -> AuthorizationDecision.allow(2, AuthorizationRiskLevel.HIGH),
                telemetry, dependencies, degraded,
                new AuditRecorder(record -> AuditAppendResult.unavailable()), clock);
        expectFailure(AuditUnavailableException.class,
                () -> auditUnavailable.getAssistantMetrics(context, AssistantId.BUGGY));
    }

    private static void verifySupervisorHealthModel() {
        MutableClock clock = new MutableClock(START);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        telemetry.recordRequest(AssistantId.BUGGY, Duration.ofMillis(20), true);
        telemetry.recordRequest(AssistantId.BUGGY, Duration.ofMillis(40), false);
        telemetry.setActiveConversations(AssistantId.BUGGY, 3);
        telemetry.setQueueDepth(AssistantId.BUGGY, 2);
        DependencyHealthRegistry dependencies = readyDependencies(clock);
        CapabilityDegradationRegistry degraded = new CapabilityDegradationRegistry();
        RecordingAuditPort audits = new RecordingAuditPort();
        AssistantSupervisorReadService service = new AssistantSupervisorReadService(
                (ctx, target) -> AuthorizationDecision.allow(3, AuthorizationRiskLevel.HIGH),
                telemetry, dependencies, degraded, new AuditRecorder(audits), clock);
        var report = service.getAssistantHealth(readContext(clock), AssistantId.BUGGY);
        require(report.status().name().equals("DEGRADED"),
                "future unconfigured dependencies keep aggregate health honest/degraded");
        require(report.metrics().requests() == 2 && report.metrics().errors() == 1,
                "health model carries request/error metrics");
        require(report.metrics().successRate() == 0.5 && report.metrics().errorRate() == 0.5,
                "health model calculates success/error rates");

        degraded.markDegraded(AssistantId.BUGGY, AssistantCapabilities.KNOWLEDGE_QUERY);
        require(service.getDegradedCapabilities(readContext(clock), AssistantId.BUGGY)
                        .contains(AssistantCapabilities.KNOWLEDGE_QUERY),
                "degraded capability projection is readable through security-gated port");
    }

    private static void verifySelfHealingSecurityAndSuccess() {
        MutableClock clock = new MutableClock(START);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        RecordingAuditPort audits = new RecordingAuditPort();
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry();
        ToolDescriptor descriptor = selfHealDescriptor(
                "tool.selfheal.cache.refresh", ToolRiskLevel.LOW, ToolAuditPolicy.CRITICAL_REQUIRED);
        registry.register(descriptor, context -> {
            executions.incrementAndGet();
            return new ToolOutput(Map.of("result", "refreshed"), DataClassification.INTERNAL);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ToolInvocation invocation = invocation(descriptor.toolId(), "refresh", "cache:knowledge", clock);
            ToolRuntime deniedRuntime = runtime(
                    registry, audits, metrics, executor, clock,
                    (tool, call) -> AuthorizationDecision.deny(
                            AuthorizationDenialReason.KILL_SWITCH_ACTIVE, 5, AuthorizationRiskLevel.LOW));
            SelfHealingService denied = selfHealing(
                    registry, deniedRuntime, telemetry, metrics, audits, clock,
                    new SelfHealingPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1), 5,
                            Duration.ofSeconds(1), 2), new ArrayList<>());
            var deniedResult = denied.attempt(invocation);
            require(deniedResult.status() == SelfHealingStatus.FAILED
                            && deniedResult.safeCode().equals("AUTHORIZATION_DENIED"),
                    "Phase-3 security denial must stop automatic self-heal");
            require(executions.get() == 0, "denied self-heal must never reach handler");

            clock.advance(Duration.ofSeconds(2));
            ToolRuntime allowedRuntime = runtime(
                    registry, audits, metrics, executor, clock,
                    (tool, call) -> AuthorizationDecision.allow(6, AuthorizationRiskLevel.LOW));
            SelfHealingService allowed = selfHealing(
                    registry, allowedRuntime, telemetry, metrics, audits, clock,
                    new SelfHealingPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1), 5,
                            Duration.ofSeconds(1), 2), new ArrayList<>());
            var success = allowed.attempt(invocation);
            require(success.status() == SelfHealingStatus.SUCCEEDED, "authorized LOW self-heal succeeds");
            require(executions.get() == 1, "authorized self-heal reaches typed handler exactly once");
            require(metrics.snapshot().scalar(AssistantMetric.SELF_HEALING_ATTEMPTS) >= 2,
                    "automatic self-healing attempts are observable");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyBudgetsAndLoopSuppression() {
        MutableClock clock = new MutableClock(START);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        RecordingAuditPort audits = new RecordingAuditPort();
        ToolRegistry registry = new ToolRegistry();
        ToolDescriptor descriptor = selfHealDescriptor(
                "tool.selfheal.loop", ToolRiskLevel.LOW, ToolAuditPolicy.CRITICAL_REQUIRED);
        registry.register(descriptor, context -> new ToolOutput(Map.of("result", "ok"), DataClassification.INTERNAL));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ToolRuntime runtime = runtime(registry, audits, metrics, executor, clock,
                    (tool, call) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            SelfHealingPolicy policy = new SelfHealingPolicy(
                    2, Duration.ofSeconds(5), Duration.ofMinutes(1), 2, Duration.ofSeconds(20), 2);
            SelfHealingService service = selfHealing(registry, runtime, telemetry, metrics, audits, clock, policy, new ArrayList<>());
            ToolInvocation a = invocation(descriptor.toolId(), "repair", "target:a", clock);
            ToolInvocation b = invocation(descriptor.toolId(), "repair", "target:b", clock);
            ToolInvocation c = invocation(descriptor.toolId(), "repair", "target:c", clock);

            require(service.attempt(a).status() == SelfHealingStatus.SUCCEEDED, "first attempt succeeds");
            require(service.attempt(a).safeCode().equals(SelfHealingSuppressionReason.COOLDOWN.name()),
                    "global cooldown prevents tight loop");
            clock.advance(Duration.ofSeconds(6));
            require(service.attempt(a).safeCode().equals(SelfHealingSuppressionReason.SAME_TARGET_SUPPRESSED.name()),
                    "same target suppression blocks repeated action");
            require(service.attempt(b).status() == SelfHealingStatus.SUCCEEDED,
                    "different target allowed after global cooldown");
            clock.advance(Duration.ofSeconds(6));
            require(service.attempt(c).safeCode().equals(SelfHealingSuppressionReason.WINDOW_BUDGET_EXCEEDED.name()),
                    "sliding window budget prevents action flood");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyFailureEscalationCreatesReportAndIncidentReference() {
        MutableClock clock = new MutableClock(START);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        RecordingAuditPort audits = new RecordingAuditPort();
        ToolRegistry registry = new ToolRegistry();
        ToolDescriptor descriptor = selfHealDescriptor(
                "tool.selfheal.failure", ToolRiskLevel.LOW, ToolAuditPolicy.CRITICAL_REQUIRED);
        registry.register(descriptor, context -> { throw new IllegalStateException("simulated internal handler failure"); });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<SelfHealingEscalation> escalations = new ArrayList<>();
        try {
            ToolRuntime runtime = runtime(registry, audits, metrics, executor, clock,
                    (tool, call) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            SelfHealingPolicy policy = new SelfHealingPolicy(
                    2, Duration.ofSeconds(1), Duration.ofMinutes(1), 10, Duration.ofSeconds(1), 2);
            SelfHealingService service = selfHealing(registry, runtime, telemetry, metrics, audits, clock, policy, escalations);
            ToolInvocation invocation = invocation(descriptor.toolId(), "repair", "target:failure", clock);
            require(service.attempt(invocation).status() == SelfHealingStatus.FAILED,
                    "first failure does not escalate before threshold");
            clock.advance(Duration.ofSeconds(2));
            var second = service.attempt(invocation);
            require(second.status() == SelfHealingStatus.ESCALATED, "second consecutive failure escalates");
            require(second.escalation() != null
                            && "report-phase11".equals(second.escalation().reportId())
                            && "incident-phase11".equals(second.escalation().incidentId()),
                    "failure escalation creates report and incident references");
            require(escalations.size() == 1 && escalations.getFirst().consecutiveFailures() == 2,
                    "one bounded escalation emitted at threshold");
            clock.advance(Duration.ofSeconds(2));
            require(service.attempt(invocation).safeCode().equals(SelfHealingSuppressionReason.MAX_ATTEMPTS_EXCEEDED.name()),
                    "max target attempts prevent post-escalation loop");
            require(telemetry.recentFailures(AssistantId.BUGGY, 10).stream()
                            .anyMatch(event -> event.category().name().equals("SELF_HEALING")),
                    "self-healing failures appear in Supervisor telemetry");
        } finally {
            executor.shutdownNow();
        }
    }

    private static SelfHealingService selfHealing(
            ToolRegistry registry,
            ToolRuntime runtime,
            InMemoryAssistantOperationalTelemetry telemetry,
            InMemoryAssistantMetrics metrics,
            RecordingAuditPort audits,
            MutableClock clock,
            SelfHealingPolicy policy,
            List<SelfHealingEscalation> escalations) {
        return new SelfHealingService(
                registry,
                runtime,
                policy,
                new SelfHealingBudgetTracker(),
                escalation -> {
                    escalations.add(escalation);
                    return new SelfHealingEscalationReceipt("report-phase11", "incident-phase11");
                },
                telemetry,
                metrics,
                new AuditRecorder(audits),
                clock);
    }

    private static ToolRuntime runtime(
            ToolRegistry registry,
            RecordingAuditPort audits,
            InMemoryAssistantMetrics metrics,
            ExecutorService executor,
            Clock clock,
            de.legitserver.assistant.runtime.policy.ToolAuthorizationPort authorization) {
        return new ToolRuntime(
                registry,
                authorization,
                new ToolSchemaValidator(),
                (descriptor, invocation, now) -> {},
                new ToolOutputFilter(new SecretGuard()),
                new ToolAudit(new AuditRecorder(audits)),
                metrics,
                executor,
                clock);
    }

    private static ToolDescriptor selfHealDescriptor(
            String toolId, ToolRiskLevel risk, ToolAuditPolicy auditPolicy) {
        return new ToolDescriptor(
                new ToolId(toolId),
                AssistantCapabilities.SELF_HEAL_LOW,
                risk,
                INPUT,
                OUTPUT,
                Duration.ofSeconds(1),
                1,
                auditPolicy,
                new ToolClassificationPolicy(DataClassification.INTERNAL, DataClassification.INTERNAL),
                true);
    }

    private static ToolInvocation invocation(ToolId toolId, String action, String resourceId, Clock clock) {
        ActorContext actor = actor(clock);
        return new ToolInvocation(
                toolId,
                servicePrincipal(clock),
                actor,
                assistant(clock),
                new ResourceRef("self_heal_target", resourceId, DataClassification.INTERNAL),
                Map.of("action", action),
                DataClassification.INTERNAL,
                null);
    }

    private static SupervisorReadContext readContext(Clock clock) {
        return new SupervisorReadContext(servicePrincipal(clock), actor(clock), assistant(clock));
    }

    private static ActorContext actor(Clock clock) {
        return new ActorContext(
                new ActorId("actor:phase11"),
                ActorType.HUMAN,
                Origin.INTERNAL,
                "network:phase11",
                ACTOR_UUID,
                null,
                null,
                REQUEST_ID,
                new TraceId("trace-phase11"),
                "test-server",
                1,
                clock.instant());
    }

    private static AssistantDescriptor assistant(Clock clock) {
        return new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                "capabilities-buggy",
                "policy-buggy",
                "knowledge-buggy",
                "runtime-buggy",
                "personality-buggy",
                1,
                clock.instant());
    }

    private static ServicePrincipal servicePrincipal(Clock clock) {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        ServiceTokenClaims claims = new ServiceTokenClaims(
                "phase11-test",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                clock.instant().minusSeconds(1),
                clock.instant().plusSeconds(60),
                "phase11-jti-" + clock.instant().getEpochSecond(),
                REQUEST_ID);
        String token = tokens.issue(claims);
        var result = tokens.verify(token, "legit-assistant", REQUEST_ID, Set.of(ServiceScopes.INVOKE), clock.instant());
        require(result.authenticated(), "test principal authenticates");
        return result.principal();
    }

    private static DependencyHealthRegistry readyDependencies(Clock clock) {
        DependencyHealthRegistry dependencies = new DependencyHealthRegistry(clock);
        dependencies.update(DependencyId.NETWORKCORE, DependencyState.UP, "UP", 2);
        dependencies.update(DependencyId.DATABASE, DependencyState.UP, "UP", 3);
        return dependencies;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Class<? extends Throwable> expected, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("expected " + expected.getSimpleName() + " but got " + failure, failure);
        }
        throw new AssertionError("expected " + expected.getSimpleName());
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public AuditAppendResult append(AuditRecord record) {
            records.add(record);
            return AuditAppendResult.recordedResult();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }

        private void advance(Duration duration) { instant = instant.plus(duration); }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return instant; }
    }
}
