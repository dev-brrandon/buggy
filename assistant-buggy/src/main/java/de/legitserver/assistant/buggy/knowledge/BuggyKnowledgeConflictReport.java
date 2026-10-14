package de.legitserver.assistant.buggy.knowledge;

import java.util.List;
import java.util.Objects;

/** Deterministic conflict report for one explicit semantic conflict key. */
public record BuggyKnowledgeConflictReport(
        BuggyKnowledgeConflictStatus status,
        String conflictKey,
        String candidateValue,
        List<BuggyKnowledgeConflict> conflicts) {

    public BuggyKnowledgeConflictReport {
        Objects.requireNonNull(status, "status");
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        if (status == BuggyKnowledgeConflictStatus.CONFLICT && conflicts.isEmpty()) {
            throw new IllegalArgumentException("CONFLICT requires at least one conflicting document");
        }
        if (status != BuggyKnowledgeConflictStatus.CONFLICT && !conflicts.isEmpty()) {
            throw new IllegalArgumentException("non-CONFLICT status must not carry conflicts");
        }
    }
}
