package de.legitserver.assistant.panel.security;

/** Safe bounded verification result for Panel user-context tokens. */
public enum PanelUserContextStatus {
    VALID,
    MALFORMED,
    SIGNATURE_INVALID,
    REQUEST_MISMATCH,
    TRACE_MISMATCH,
    SERVICE_BINDING_MISMATCH,
    NOT_YET_VALID,
    EXPIRED,
    REPLAYED
}
