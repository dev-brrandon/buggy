package de.legitserver.assistant.pipeline.control;

import de.legitserver.assistant.core.request.RequestId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks active requests and propagates cancellation to the running execution future. */
public final class PipelineCancellationRegistry {
    private final ConcurrentHashMap<RequestId, State> states = new ConcurrentHashMap<>();

    public void register(RequestId requestId) {
        if (requestId == null) throw new IllegalArgumentException("requestId must not be null");
        State previous = states.putIfAbsent(requestId, new State());
        if (previous != null) throw new IllegalStateException("request already active");
    }

    public boolean cancel(RequestId requestId) {
        if (requestId == null) throw new IllegalArgumentException("requestId must not be null");
        State state = states.get(requestId);
        if (state == null) return false;
        state.cancelled.set(true);
        Future<?> future = state.future;
        if (future != null) future.cancel(true);
        return true;
    }

    public boolean cancelled(RequestId requestId) {
        State state = states.get(requestId);
        return state != null && state.cancelled.get();
    }

    public void attachFuture(RequestId requestId, Future<?> future) {
        if (future == null) throw new IllegalArgumentException("future must not be null");
        State state = states.get(requestId);
        if (state == null) throw new IllegalStateException("request not active");
        state.future = future;
        if (state.cancelled.get()) future.cancel(true);
    }

    public void complete(RequestId requestId) {
        if (requestId != null) states.remove(requestId);
    }

    private static final class State {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Future<?> future;
    }
}
