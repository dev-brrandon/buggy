package de.legitserver.assistant.requests.model;

/** Canonical Request / Approval lifecycle. */
public enum RequestState {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CANCELLED,
    EXECUTING,
    EXECUTED,
    FAILED
}
