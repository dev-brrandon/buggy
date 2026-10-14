package de.legitserver.assistant.buggy.tasks;

public enum BuggyTaskStatus {
    OPEN,
    ACTIVE,
    WAITING_DEPENDENCY,
    WAITING_APPROVAL,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
