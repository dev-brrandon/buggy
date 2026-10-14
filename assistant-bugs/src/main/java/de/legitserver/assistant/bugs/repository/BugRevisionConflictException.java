package de.legitserver.assistant.bugs.repository;

import de.legitserver.assistant.bugs.model.InternalBugId;

public final class BugRevisionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BugRevisionConflictException(InternalBugId id, long expectedRevision) {
        super("Bug revision conflict for " + id.value() + ", expected " + expectedRevision);
    }
}
