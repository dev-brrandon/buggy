package de.legitserver.assistant.security.authn;

/** Stable machine-readable service-authentication outcome. */
public enum ServiceAuthenticationStatus {
    AUTHENTICATED,
    MALFORMED,
    SIGNATURE_INVALID,
    AUDIENCE_MISMATCH,
    REQUEST_MISMATCH,
    EXPIRED,
    NOT_YET_VALID,
    REPLAYED,
    SCOPE_MISSING
}
