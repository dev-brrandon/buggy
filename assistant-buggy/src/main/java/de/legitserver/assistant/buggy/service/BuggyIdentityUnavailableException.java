package de.legitserver.assistant.buggy.service;

/** Fail-closed signal used when the persistent Buggy identity has not been provisioned. */
public final class BuggyIdentityUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public BuggyIdentityUnavailableException() {
        super("Persistent Buggy identity is not provisioned");
    }
}
