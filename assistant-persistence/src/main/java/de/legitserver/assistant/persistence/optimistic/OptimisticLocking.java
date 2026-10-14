package de.legitserver.assistant.persistence.optimistic;

/** Common update-count guard for SQL of the form WHERE id = ? AND revision = ?. */
public final class OptimisticLocking {
    private OptimisticLocking() {}

    public static void requireExactlyOne(
            int updatedRows, String entity, String id, long expectedRevision) {
        if (updatedRows == 1) {
            return;
        }
        if (updatedRows == 0) {
            throw new OptimisticLockConflictException(entity, id, expectedRevision);
        }
        throw new IllegalStateException(
                "Persistence invariant violated: expected one row, updated " + updatedRows);
    }
}
