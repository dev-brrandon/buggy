package de.legitserver.assistant.bugs.repository;

import de.legitserver.assistant.bugs.model.InternalBugId;

public final class BugNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BugNotFoundException(InternalBugId id) {
        super("Bug not found: " + id.value());
    }
}
