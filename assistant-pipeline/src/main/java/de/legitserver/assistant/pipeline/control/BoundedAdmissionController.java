package de.legitserver.assistant.pipeline.control;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded concurrency + bounded wait queue. Requests beyond both limits are rejected as backpressure. */
public final class BoundedAdmissionController {
    private final Semaphore active;
    private final int maxQueued;
    private final Duration pollInterval;
    private final AtomicInteger queued = new AtomicInteger();
    private final AssistantMetrics metrics;

    public BoundedAdmissionController(int maxConcurrency, int maxQueued, Duration pollInterval, AssistantMetrics metrics) {
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        if (maxQueued < 0) throw new IllegalArgumentException("maxQueued must be >= 0");
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (metrics == null) throw new IllegalArgumentException("metrics must not be null");
        this.active = new Semaphore(maxConcurrency, true);
        this.maxQueued = maxQueued;
        this.pollInterval = pollInterval;
        this.metrics = metrics;
    }

    public AdmissionDecision acquire(RequestId requestId, PipelineCancellationRegistry cancellations, Duration maxWait) {
        if (requestId == null || cancellations == null || maxWait == null) {
            throw new IllegalArgumentException("admission arguments must not be null");
        }
        if (active.tryAcquire()) return AdmissionDecision.ACQUIRED;
        if (maxQueued == 0) return AdmissionDecision.BACKPRESSURE;
        int position = queued.incrementAndGet();
        metrics.setGauge(AssistantMetric.QUEUE_DEPTH, queued.get());
        if (position > maxQueued) {
            int depth = queued.decrementAndGet();
            metrics.setGauge(AssistantMetric.QUEUE_DEPTH, depth);
            return AdmissionDecision.BACKPRESSURE;
        }
        long deadline = System.nanoTime() + maxWait.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                if (cancellations.cancelled(requestId)) return AdmissionDecision.CANCELLED;
                long remaining = deadline - System.nanoTime();
                long waitNanos = Math.min(pollInterval.toNanos(), Math.max(1L, remaining));
                try {
                    if (active.tryAcquire(waitNanos, java.util.concurrent.TimeUnit.NANOSECONDS)) {
                        return AdmissionDecision.ACQUIRED;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return AdmissionDecision.CANCELLED;
                }
            }
            return AdmissionDecision.BACKPRESSURE;
        } finally {
            int depth = queued.decrementAndGet();
            metrics.setGauge(AssistantMetric.QUEUE_DEPTH, Math.max(0, depth));
        }
    }

    public void release() {
        active.release();
    }

    public int availablePermits() {
        return active.availablePermits();
    }
}
