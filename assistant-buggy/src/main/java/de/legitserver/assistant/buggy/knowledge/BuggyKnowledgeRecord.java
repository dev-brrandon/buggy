package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import java.util.Objects;

/** ACL-checked Buggy view of a Foundation Knowledge document and current immutable revision. */
public record BuggyKnowledgeRecord(
        KnowledgeDocument document,
        KnowledgeRevision revision,
        BuggyKnowledgeArea area,
        BuggyKnowledgeVisibility visibility,
        BuggyKnowledgeConfidence confidence,
        BuggyKnowledgeState state) {

    public BuggyKnowledgeRecord {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(state, "state");
    }
}
