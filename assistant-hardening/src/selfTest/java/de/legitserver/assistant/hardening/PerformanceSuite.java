package de.legitserver.assistant.hardening;

import de.legitserver.assistant.bugs.model.BugDraft;
import de.legitserver.assistant.bugs.model.BugPriority;
import de.legitserver.assistant.bugs.model.BugSeverity;
import de.legitserver.assistant.bugs.model.BugSource;
import de.legitserver.assistant.bugs.model.BugTargetMapping;
import de.legitserver.assistant.bugs.model.PublicBugId;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessPolicy;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.pipeline.control.AdmissionDecision;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.policy.RateLimitKey;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.supervisor.health.InMemoryAssistantOperationalTelemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Micro-load verification for the seven required non-AI internal surfaces. */
final class PerformanceSuite {
    private static final Instant NOW = Instant.parse("2026-10-04T15:15:00Z");
    private static final int WARMUP = 1_500;
    private static final int SAMPLES = 6_000;
    private static final double MAX_P50_MS = 50.0;
    private static final double MAX_P95_MS = 150.0;
    private static final double MAX_P99_MS = 300.0;
    private static volatile long blackhole;

    private PerformanceSuite() {}

    static void run() {
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmark("Conversations", PerformanceSuite::conversationOperation));
        results.add(benchmark("Knowledge", knowledgeOperation()));
        results.add(benchmark("Bugs", PerformanceSuite::bugOperation));
        results.add(benchmark("Authorization", authorizationOperation()));
        results.add(benchmark("Rate Limiting", rateLimitOperation()));
        results.add(benchmark("Supervisor Metrics", supervisorMetricsOperation()));
        results.add(benchmark("Tool Queue", toolQueueOperation()));
        for (BenchmarkResult result : results) {
            require(result.p50Ms() < MAX_P50_MS, result.name() + " P50 exceeded budget: " + result.p50Ms());
            require(result.p95Ms() < MAX_P95_MS, result.name() + " P95 exceeded budget: " + result.p95Ms());
            require(result.p99Ms() < MAX_P99_MS, result.name() + " P99 exceeded budget: " + result.p99Ms());
            System.out.printf(java.util.Locale.ROOT,
                    "PHASE16_LOAD_SURFACE: PASS %-18s P50=%.3fms P95=%.3fms P99=%.3fms%n",
                    result.name(), result.p50Ms(), result.p95Ms(), result.p99Ms());
        }
        System.out.println("PHASE16_LOAD_TESTS: PASS surfaces=7 samplesPerSurface=" + SAMPLES);
    }

    private static void conversationOperation() {
        boolean allowed = ConversationStateMachineHolder.INSTANCE.canTransition(ConversationState.ACTIVE, ConversationState.PROCESSING);
        blackhole ^= allowed ? 1L : 0L;
    }

    private static Runnable knowledgeOperation() {
        ActorId actor = new ActorId("actor:phase16-benchmark");
        KnowledgeDocument document = new KnowledgeDocument(
                KnowledgeDocumentId.random(), KnowledgeDomain.SERVER_OPERATIONS, "Operations Runbook",
                DataClassification.INTERNAL, KnowledgeDocumentStatus.PUBLISHED, 1, NOW.minusSeconds(1), actor,
                NOW.minusSeconds(60), actor, NOW, KnowledgeAcl.openWithinPolicy());
        KnowledgeAccessContext context = new KnowledgeAccessContext(
                actor, true, Set.of(), Set.of(KnowledgeDomain.SERVER_OPERATIONS), DataClassification.INTERNAL);
        KnowledgeAccessPolicy policy = new KnowledgeAccessPolicy();
        return () -> blackhole ^= policy.evaluate(document, context).allowed() ? 2L : 0L;
    }

    private static void bugOperation() {
        BugDraft draft = new BugDraft(
                PublicBugId.of("HARD-0016"), BugSeverity.MEDIUM, BugPriority.P2,
                new ActorId("actor:phase16-benchmark"), BugSource.of("ASSISTANT"),
                "Benchmark report", "Summary", new BugTargetMapping("assistant-api", "0.1.0-dev.16", "build-16", "assistant", "test"),
                DataClassification.INTERNAL, Map.of("suite", "phase16"));
        blackhole ^= draft.originalReport().length();
    }

    private static Runnable authorizationOperation() {
        SecurityAttackSuite.SecurityFixture fixture = new SecurityAttackSuite.SecurityFixture();
        require(fixture.authorizeOnce(), "authorization benchmark fixture must be allowed");
        return () -> blackhole ^= fixture.authorizeOnce() ? 4L : 0L;
    }

    private static Runnable rateLimitOperation() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                WARMUP + SAMPLES + 100, Duration.ofMinutes(1), 16, Clock.fixed(NOW, ZoneOffset.UTC));
        RateLimitKey key = new RateLimitKey(
                new ActorId("actor:phase16-rate"), AssistantId.BUGGY, AssistantCapabilities.DIAGNOSTICS_READ);
        return () -> blackhole ^= limiter.tryAcquire(key) ? 8L : 0L;
    }

    private static Runnable supervisorMetricsOperation() {
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(Clock.fixed(NOW, ZoneOffset.UTC));
        for (int i = 0; i < 512; i++) telemetry.recordRequest(AssistantId.BUGGY, Duration.ofMillis(i % 25), true);
        return () -> blackhole ^= telemetry.snapshot(AssistantId.BUGGY).requests();
    }

    private static Runnable toolQueueOperation() {
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        BoundedAdmissionController controller = new BoundedAdmissionController(8, 16, Duration.ofMillis(1), metrics);
        PipelineCancellationRegistry cancellations = new PipelineCancellationRegistry();
        return () -> {
            RequestId id = new RequestId("req-phase16-q-" + Long.toUnsignedString(System.nanoTime()));
            cancellations.register(id);
            AdmissionDecision decision = controller.acquire(id, cancellations, Duration.ofMillis(5));
            if (decision == AdmissionDecision.ACQUIRED) controller.release();
            cancellations.complete(id);
            blackhole ^= decision.ordinal();
        };
    }

    private static BenchmarkResult benchmark(String name, Runnable operation) {
        for (int i = 0; i < WARMUP; i++) operation.run();
        long[] nanos = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            operation.run();
            nanos[i] = Math.max(0L, System.nanoTime() - started);
        }
        java.util.Arrays.sort(nanos);
        return new BenchmarkResult(name, millis(percentile(nanos, 0.50)), millis(percentile(nanos, 0.95)), millis(percentile(nanos, 0.99)));
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private record BenchmarkResult(String name, double p50Ms, double p95Ms, double p99Ms) {}

    private static final class ConversationStateMachineHolder {
        private static final ConversationStateMachine INSTANCE = new ConversationStateMachine();
    }
}
