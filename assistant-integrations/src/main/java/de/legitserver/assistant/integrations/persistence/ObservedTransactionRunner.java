package de.legitserver.assistant.integrations.persistence;

import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import de.legitserver.assistant.persistence.transaction.TransactionWork;
import java.time.Duration;

/** TransactionRunner decorator measuring database latency without changing persistence semantics. */
public final class ObservedTransactionRunner implements TransactionRunner {
    private final TransactionRunner delegate;
    private final AssistantMetrics metrics;

    public ObservedTransactionRunner(TransactionRunner delegate, AssistantMetrics metrics) {
        if (delegate == null || metrics == null) throw new IllegalArgumentException("transaction observer dependency must not be null");
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public <T> T required(TransactionWork<T> work) {
        return measure(() -> delegate.required(work));
    }

    @Override
    public <T> T readOnly(TransactionWork<T> work) {
        return measure(() -> delegate.readOnly(work));
    }

    private <T> T measure(Operation<T> operation) {
        long started = System.nanoTime();
        try {
            return operation.run();
        } finally {
            metrics.recordLatency(
                    AssistantMetric.DATABASE_LATENCY,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - started)));
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }
}
