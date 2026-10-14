package de.legitserver.assistant.persistence.optimistic;

/** Stale revision attempted to mutate a versioned record. */
public final class OptimisticLockConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OptimisticLockConflictException(String entity, String id, long expectedRevision) {
        super("Optimistic lock conflict for " + entity + " " + id + " at revision " + expectedRevision);
    }
}
