package de.legitserver.assistant.knowledge.model;

import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.util.Set;

/** Document-specific additive NetworkCore permission requirements. */
public record KnowledgeAcl(Set<String> requiredPermissions) {
    public KnowledgeAcl {
        requiredPermissions = KnowledgeValidation.permissions(requiredPermissions);
    }

    public static KnowledgeAcl openWithinPolicy() {
        return new KnowledgeAcl(Set.of());
    }
}
