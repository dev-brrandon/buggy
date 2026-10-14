package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.metrics.LatencySnapshot;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe bounded in-memory telemetry with fixed Assistant IDs and bounded failure history. */
public final class InMemoryAssistantOperationalTelemetry implements AssistantOperationalTelemetry {
    private static final int LATENCY_WINDOW = 512;
    private static final int FAILURE_WINDOW = 128;

    private final Clock clock;
    private final EnumMap<AssistantId, State> states = new EnumMap<>(AssistantId.class);

    public InMemoryAssistantOperationalTelemetry() {
        this(Clock.systemUTC());
    }

    public InMemoryAssistantOperationalTelemetry(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.clock = clock;
        for (AssistantId assistantId : AssistantId.values()) states.put(assistantId, new State());
    }

    @Override
    public void recordRequest(AssistantId assistantId, Duration latency, boolean success) {
        requireDuration(latency);
        State state = state(assistantId);
        synchronized (state) {
            state.requests++;
            if (success) state.successes++;
            else state.errors++;
            state.latencies.record(latency.toMillis());
            if (!success) addFailure(state, new AssistantFailureEvent(
                    assistantId, AssistantFailureCategory.REQUEST, "REQUEST_FAILED", null, null,
                    "assistant:" + assistantId.name().toLowerCase(), clock.instant()));
        }
    }

    @Override
    public void recordToolCall(
            AssistantId assistantId,
            ToolId toolId,
            AssistantCapabilityId capability,
            Duration latency,
            ToolFailureCode failureCode,
            String target,
            Instant at) {
        requireDuration(latency);
        if (toolId == null || capability == null || target == null || at == null) {
            throw new IllegalArgumentException("tool telemetry fields must not be null");
        }
        State state = state(assistantId);
        synchronized (state) {
            state.toolCalls++;
            if (failureCode != null) {
                state.toolFailures++;
                if (failureCode == ToolFailureCode.TIMEOUT) state.timeouts++;
                AssistantFailureCategory category = failureCode == ToolFailureCode.TIMEOUT
                        ? AssistantFailureCategory.TIMEOUT : AssistantFailureCategory.TOOL;
                addFailure(state, new AssistantFailureEvent(
                        assistantId, category, failureCode.name(), capability, toolId, target, at));
                MutableToolFailure summary = state.toolFailuresById.computeIfAbsent(toolId, ignored -> new MutableToolFailure());
                summary.failures++;
                if (failureCode == ToolFailureCode.TIMEOUT) summary.timeouts++;
                summary.lastSafeCode = failureCode.name();
                summary.lastFailureAt = at;
            }
        }
    }

    @Override
    public void recordRateLimitHit(AssistantId assistantId, String target, Instant at) {
        State state = state(assistantId);
        synchronized (state) {
            state.rateLimitHits++;
            addFailure(state, new AssistantFailureEvent(
                    assistantId, AssistantFailureCategory.RATE_LIMIT, "RATE_LIMIT_HIT", null, null, target, at));
        }
    }

    @Override
    public void recordAuthorizationDenial(
            AssistantId assistantId, AssistantCapabilityId capability, String target, Instant at) {
        State state = state(assistantId);
        synchronized (state) {
            state.authorizationDenials++;
            addFailure(state, new AssistantFailureEvent(
                    assistantId, AssistantFailureCategory.AUTHORIZATION, "AUTHORIZATION_DENIED",
                    capability, null, target, at));
        }
    }

    @Override
    public void recordSelfHealingFailure(
            AssistantId assistantId,
            ToolId toolId,
            AssistantCapabilityId capability,
            String safeCode,
            String target,
            Instant at) {
        State state = state(assistantId);
        synchronized (state) {
            addFailure(state, new AssistantFailureEvent(
                    assistantId, AssistantFailureCategory.SELF_HEALING, safeCode, capability, toolId, target, at));
        }
    }

    @Override
    public void setActiveConversations(AssistantId assistantId, long value) {
        if (value < 0) throw new IllegalArgumentException("activeConversations must not be negative");
        State state = state(assistantId);
        synchronized (state) { state.activeConversations = value; }
    }

    @Override
    public void setQueueDepth(AssistantId assistantId, long value) {
        if (value < 0) throw new IllegalArgumentException("queueDepth must not be negative");
        State state = state(assistantId);
        synchronized (state) { state.queueDepth = value; }
    }

    @Override
    public AssistantMetricsSnapshot snapshot(AssistantId assistantId) {
        State state = state(assistantId);
        synchronized (state) {
            double successRate = state.requests == 0 ? 1.0 : ((double) state.successes / state.requests);
            double errorRate = state.requests == 0 ? 0.0 : ((double) state.errors / state.requests);
            return new AssistantMetricsSnapshot(
                    assistantId,
                    clock.instant(),
                    state.requests,
                    state.successes,
                    state.errors,
                    successRate,
                    errorRate,
                    state.latencies.snapshot(),
                    state.toolCalls,
                    state.toolFailures,
                    state.timeouts,
                    state.rateLimitHits,
                    state.authorizationDenials,
                    state.activeConversations,
                    state.queueDepth);
        }
    }

    @Override
    public List<AssistantFailureEvent> recentFailures(AssistantId assistantId, int limit) {
        if (limit < 1 || limit > FAILURE_WINDOW) throw new IllegalArgumentException("limit out of range");
        State state = state(assistantId);
        synchronized (state) {
            List<AssistantFailureEvent> result = new ArrayList<>(Math.min(limit, state.failures.size()));
            var iterator = state.failures.descendingIterator();
            while (iterator.hasNext() && result.size() < limit) result.add(iterator.next());
            return List.copyOf(result);
        }
    }

    @Override
    public List<ToolFailureSummary> toolFailureSummary(AssistantId assistantId) {
        State state = state(assistantId);
        synchronized (state) {
            return state.toolFailuresById.entrySet().stream()
                    .map(entry -> entry.getValue().snapshot(entry.getKey()))
                    .sorted(Comparator.comparing(summary -> summary.toolId().value()))
                    .toList();
        }
    }

    private State state(AssistantId assistantId) {
        if (assistantId == null) throw new IllegalArgumentException("assistantId must not be null");
        return states.get(assistantId);
    }

    private static void requireDuration(Duration duration) {
        if (duration == null || duration.isNegative()) throw new IllegalArgumentException("latency must not be negative");
    }

    private static void addFailure(State state, AssistantFailureEvent event) {
        while (state.failures.size() >= FAILURE_WINDOW) state.failures.removeFirst();
        state.failures.addLast(event);
    }

    private static final class State {
        private long requests;
        private long successes;
        private long errors;
        private long toolCalls;
        private long toolFailures;
        private long timeouts;
        private long rateLimitHits;
        private long authorizationDenials;
        private long activeConversations;
        private long queueDepth;
        private final BoundedLatency latencies = new BoundedLatency(LATENCY_WINDOW);
        private final ArrayDeque<AssistantFailureEvent> failures = new ArrayDeque<>();
        private final Map<ToolId, MutableToolFailure> toolFailuresById = new HashMap<>();
    }

    private static final class MutableToolFailure {
        private long failures;
        private long timeouts;
        private String lastSafeCode;
        private Instant lastFailureAt;

        private ToolFailureSummary snapshot(ToolId toolId) {
            return new ToolFailureSummary(toolId, failures, timeouts, lastSafeCode, lastFailureAt);
        }
    }

    private static final class BoundedLatency {
        private final long[] samples;
        private long totalCount;
        private double totalMs;
        private int size;
        private int cursor;

        private BoundedLatency(int capacity) { samples = new long[capacity]; }

        private void record(long millis) {
            samples[cursor] = Math.max(0L, millis);
            cursor = (cursor + 1) % samples.length;
            if (size < samples.length) size++;
            totalCount++;
            totalMs += Math.max(0L, millis);
        }

        private LatencySnapshot snapshot() {
            if (size == 0) return LatencySnapshot.empty();
            long[] recent = Arrays.copyOf(samples, size);
            Arrays.sort(recent);
            return new LatencySnapshot(
                    totalCount,
                    size,
                    totalMs / totalCount,
                    recent[0],
                    recent[size - 1],
                    percentile(recent, 0.50),
                    percentile(recent, 0.95),
                    percentile(recent, 0.99));
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }
    }
}
