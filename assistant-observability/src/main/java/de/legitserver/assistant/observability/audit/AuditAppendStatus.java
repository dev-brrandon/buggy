package de.legitserver.assistant.observability.audit;

/** Result of attempting to append to the canonical audit boundary. */
public enum AuditAppendStatus {
    RECORDED,
    UNAVAILABLE,
    REJECTED
}
