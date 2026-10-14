package de.legitserver.assistant.runtime.model;

/** Audit policy. Tool execution never supports an unaudited mode. */
public enum ToolAuditPolicy {
    REQUIRED(false),
    CRITICAL_REQUIRED(true);

    private final boolean failClosed;

    ToolAuditPolicy(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public boolean failClosed() {
        return failClosed;
    }
}
