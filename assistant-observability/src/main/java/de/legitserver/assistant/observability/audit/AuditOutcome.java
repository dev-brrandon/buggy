package de.legitserver.assistant.observability.audit;

/** Safe, bounded outcome taxonomy for audit records. */
public enum AuditOutcome {
    REQUESTED,
    SUCCESS,
    DENIED,
    FAILED
}
