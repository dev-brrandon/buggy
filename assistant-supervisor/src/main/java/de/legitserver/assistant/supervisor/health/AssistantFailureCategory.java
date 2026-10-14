package de.legitserver.assistant.supervisor.health;

/** Sanitized bounded failure categories for Supervisor inspection. */
public enum AssistantFailureCategory {
    REQUEST,
    TOOL,
    TIMEOUT,
    AUTHORIZATION,
    RATE_LIMIT,
    DEPENDENCY,
    SELF_HEALING
}
