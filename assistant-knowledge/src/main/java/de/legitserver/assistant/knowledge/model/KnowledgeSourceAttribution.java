package de.legitserver.assistant.knowledge.model;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable provenance metadata carried with every immutable Knowledge revision. */
public record KnowledgeSourceAttribution(String sourceKind, String sourceRef, String sourceLabel) {
    public KnowledgeSourceAttribution {
        sourceKind = CoreValidation.requireSafeToken(sourceKind, "sourceKind", 64);
        sourceRef = CoreValidation.requireText(sourceRef, "sourceRef", 1024);
        sourceLabel = CoreValidation.optionalText(sourceLabel, "sourceLabel", 256);
    }
}
