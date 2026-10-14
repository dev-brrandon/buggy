package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.classification.DataClassification;

/** Product-level visibility names mapped onto the accepted Foundation classification ladder. */
public enum BuggyKnowledgeVisibility {
    PUBLIC(DataClassification.PUBLIC),
    INTERNAL(DataClassification.INTERNAL),
    CONFIDENTIAL(DataClassification.CONFIDENTIAL),
    OWNER_CONFIDENTIAL(DataClassification.RESTRICTED),
    SECURITY_SENSITIVE(DataClassification.RESTRICTED),
    SECRET(DataClassification.SECRET);

    private final DataClassification foundationClassification;

    BuggyKnowledgeVisibility(DataClassification foundationClassification) {
        this.foundationClassification = foundationClassification;
    }

    public DataClassification foundationClassification() {
        return foundationClassification;
    }

    public boolean allowedAsNormalKnowledge() {
        return this != SECRET;
    }

    public boolean ownerRestricted() {
        return this == OWNER_CONFIDENTIAL;
    }

    public boolean securitySensitive() {
        return this == SECURITY_SENSITIVE;
    }
}
