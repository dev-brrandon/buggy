package de.legitserver.assistant.observability.tracing;

import java.util.Optional;

/** Request-thread correlation holder. Asynchronous work must explicitly propagate the immutable context. */
public final class CorrelationContextHolder {
    private static final ThreadLocal<TraceCorrelation> CURRENT = new ThreadLocal<>();

    private CorrelationContextHolder() {}

    public static Scope open(TraceCorrelation correlation) {
        if (correlation == null) throw new IllegalArgumentException("correlation must not be null");
        TraceCorrelation previous = CURRENT.get();
        CURRENT.set(correlation);
        return new Scope(previous);
    }

    public static Optional<TraceCorrelation> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static final class Scope implements AutoCloseable {
        private final TraceCorrelation previous;
        private boolean closed;

        private Scope(TraceCorrelation previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
            closed = true;
        }
    }
}
