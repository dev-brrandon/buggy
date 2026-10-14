package de.legitserver.assistant.hardening;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.pipeline.control.AdmissionDecision;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.policy.RateLimitKey;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.supervisor.health.InMemoryAssistantOperationalTelemetry;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded-queue concurrency probe plus retained-heap soak guard. */
final class MemorySoakSuite {
    private static final Instant NOW = Instant.parse("2026-10-04T15:30:00Z");
    private static final long MAX_RETAINED_GROWTH_BYTES = 32L * 1024L * 1024L;

    private MemorySoakSuite() {}

    static void run() throws Exception {
        verifyBoundedQueueUnderContention();
        verifyMemoryGrowthUnderSoak();
        System.out.println("PHASE16_NO_UNBOUNDED_QUEUES: PASS");
    }

    private static void verifyBoundedQueueUnderContention() throws Exception {
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        BoundedAdmissionController controller = new BoundedAdmissionController(1, 2, Duration.ofMillis(2), metrics);
        PipelineCancellationRegistry cancellations = new PipelineCancellationRegistry();
        RequestId owner = new RequestId("req-phase16-queue-owner");
        cancellations.register(owner);
        require(controller.acquire(owner, cancellations, Duration.ofMillis(10)) == AdmissionDecision.ACQUIRED,
                "owner must acquire only active slot");

        ExecutorService executor = Executors.newFixedThreadPool(6);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger backpressured = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    RequestId id = new RequestId("req-phase16-queue-" + index);
                    cancellations.register(id);
                    try {
                        AdmissionDecision result = controller.acquire(id, cancellations, Duration.ofMillis(120));
                        if (result == AdmissionDecision.ACQUIRED) {
                            acquired.incrementAndGet();
                            controller.release();
                        } else if (result == AdmissionDecision.BACKPRESSURE) {
                            backpressured.incrementAndGet();
                        }
                    } finally {
                        cancellations.complete(id);
                    }
                }));
            }
            Thread.sleep(25L);
            long queueDepth = metrics.snapshot().scalar(AssistantMetric.QUEUE_DEPTH);
            require(queueDepth <= 2L, "queue depth must never exceed configured maxQueued=2, actual=" + queueDepth);
            controller.release();
            cancellations.complete(owner);
            for (Future<?> future : futures) future.get();
            require(backpressured.get() >= 3, "excess contention must produce backpressure");
            require(acquired.get() <= 3, "owner plus at most two queued requests may drain");
        } finally {
            executor.shutdownNow();
            if (controller.availablePermits() == 0) controller.release();
            cancellations.complete(owner);
        }
    }

    private static void verifyMemoryGrowthUnderSoak() throws Exception {
        // Warm classes/JIT before the retained-heap baseline.
        soakOperations(25_000);
        long before = usedHeapAfterGc();
        soakOperations(250_000);
        long after = usedHeapAfterGc();
        long growth = Math.max(0L, after - before);
        require(growth <= MAX_RETAINED_GROWTH_BYTES,
                "retained heap growth exceeded 32 MiB: " + growth + " bytes");
        System.out.println("PHASE16_MEMORY_SOAK: PASS operations=250000 retainedGrowthBytes=" + growth);
    }

    private static void soakOperations(int operations) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BoundedReplayProtector replay = new BoundedReplayProtector(256);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1_000, Duration.ofMinutes(1), 128, clock);
        RateLimitKey key = new RateLimitKey(
                new ActorId("actor:phase16-soak"), AssistantId.BUGGY, AssistantCapabilities.DIAGNOSTICS_READ);
        InMemoryAssistantOperationalTelemetry telemetry = new InMemoryAssistantOperationalTelemetry(clock);
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        for (int i = 0; i < operations; i++) {
            replay.claim("cred-" + i, NOW.plusSeconds(60), NOW);
            limiter.tryAcquire(key);
            telemetry.recordRequest(AssistantId.BUGGY, Duration.ofMillis(i % 20), (i & 7) != 0);
            metrics.increment(AssistantMetric.REQUEST_COUNT);
            metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, Duration.ofMillis(i % 25));
            if ((i & 1023) == 0) {
                telemetry.snapshot(AssistantId.BUGGY);
                metrics.snapshot();
            }
        }
        require(replay.size() <= 256, "replay protector must remain bounded during soak");
    }

    private static long usedHeapAfterGc() throws Exception {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(60L);
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }
}
