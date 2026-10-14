package de.legitserver.assistant.observability.metrics;

/** Canonical Phase-6 metric catalog. */
public enum AssistantMetric {
    REQUEST_COUNT("legit.assistant.requests.total", MetricKind.COUNTER),
    REQUEST_LATENCY("legit.assistant.request.latency", MetricKind.LATENCY),
    REQUEST_ERRORS("legit.assistant.request.errors.total", MetricKind.COUNTER),
    AUTHORIZATION_DENIALS("legit.assistant.authorization.denials.total", MetricKind.COUNTER),
    RATE_LIMIT_HITS("legit.assistant.rate_limits.total", MetricKind.COUNTER),
    DATABASE_LATENCY("legit.assistant.database.latency", MetricKind.LATENCY),
    NETWORKCORE_LATENCY("legit.assistant.networkcore.latency", MetricKind.LATENCY),
    ACTIVE_CONVERSATIONS("legit.assistant.active_conversations", MetricKind.GAUGE),
    TOOL_CALLS("legit.assistant.tool.calls.total", MetricKind.COUNTER),
    TOOL_FAILURES("legit.assistant.tool.failures.total", MetricKind.COUNTER),
    QUEUE_DEPTH("legit.assistant.queue.depth", MetricKind.GAUGE),
    SELF_HEALING_ATTEMPTS("legit.assistant.self_healing.attempts.total", MetricKind.COUNTER),
    SELF_HEALING_FAILURES("legit.assistant.self_healing.failures.total", MetricKind.COUNTER),
    ASSISTANT_HEALTH("legit.assistant.health", MetricKind.GAUGE);

    private final String wireName;
    private final MetricKind kind;

    AssistantMetric(String wireName, MetricKind kind) {
        this.wireName = wireName;
        this.kind = kind;
    }

    public String wireName() {
        return wireName;
    }

    public MetricKind kind() {
        return kind;
    }
}
