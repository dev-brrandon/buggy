package de.legitserver.assistant.pipeline.control;

public enum IdempotencyStatus {
    OWNER,
    REPLAY,
    CONFLICT,
    IN_FLIGHT
}
