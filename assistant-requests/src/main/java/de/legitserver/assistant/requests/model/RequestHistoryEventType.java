package de.legitserver.assistant.requests.model;

/** Append-only Request aggregate history taxonomy. */
public enum RequestHistoryEventType {
    CREATED,
    PAYLOAD_REVISED,
    APPROVAL_RECORDED,
    DENIAL_RECORDED,
    EXPIRED,
    CANCELLED,
    EXECUTION_STARTED,
    EXECUTED,
    FAILED
}
