package de.legitserver.assistant.security.authorization.buggy;

/** Canonical Buggy V1 authority verbs. OWNER_ONLY is a boundary marker and is never directly granted to Buggy. */
public enum BuggyAuthorityMode {
    READ,
    EXECUTE,
    MUTATE,
    REQUEST_ONLY,
    OWNER_ONLY
}
