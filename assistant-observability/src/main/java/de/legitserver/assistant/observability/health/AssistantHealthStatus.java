package de.legitserver.assistant.observability.health;

/** Aggregate service health independent of Spring Actuator. */
public enum AssistantHealthStatus {
    UP,
    DEGRADED,
    NOT_READY
}
