package de.legitserver.assistant.buggy.service;

/** Fail-closed signal used when Buggy Personality has not yet been provisioned. */
public final class BuggyPersonalityUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public BuggyPersonalityUnavailableException() {
        super("Buggy Personality is not provisioned");
    }
}
