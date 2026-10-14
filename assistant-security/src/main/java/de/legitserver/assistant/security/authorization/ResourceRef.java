package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.validation.CoreValidation;

/** Opaque typed resource reference evaluated by resource-specific authorization policy. */
public record ResourceRef(String resourceType, String resourceId, DataClassification classification) {
    public ResourceRef {
        resourceType = CoreValidation.requireSafeToken(resourceType, "resourceType", 96);
        resourceId = CoreValidation.requireSafeToken(resourceId, "resourceId", 192);
        classification = CoreValidation.requireNonNull(classification, "classification");
    }
}
