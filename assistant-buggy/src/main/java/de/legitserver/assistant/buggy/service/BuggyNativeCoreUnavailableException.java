package de.legitserver.assistant.buggy.service;

public final class BuggyNativeCoreUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public BuggyNativeCoreUnavailableException() {
        super("Buggy Native Core is not initialized");
    }
}
