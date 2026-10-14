package de.legitserver.assistant.buggy.supervision;

/** Runtime readiness only; this is not an identity/personality model. */
public enum BuggyFutureAssistantState {
    NOT_STARTED,
    REGISTERED_DISABLED,
    AVAILABLE,
    PAUSED,
    DEGRADED,
    UNAVAILABLE
}
