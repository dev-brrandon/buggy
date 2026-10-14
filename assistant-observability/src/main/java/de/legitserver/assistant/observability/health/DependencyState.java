package de.legitserver.assistant.observability.health;

/** Safe dependency state; NOT_CONFIGURED prevents false-positive UP reports. */
public enum DependencyState {
    UP,
    DEGRADED,
    DOWN,
    NOT_CONFIGURED,
    UNKNOWN
}
