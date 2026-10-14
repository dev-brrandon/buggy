package de.legitserver.assistant.observability.metrics;

/** Supported metric semantics for the framework-free registry. */
public enum MetricKind {
    COUNTER,
    GAUGE,
    LATENCY
}
