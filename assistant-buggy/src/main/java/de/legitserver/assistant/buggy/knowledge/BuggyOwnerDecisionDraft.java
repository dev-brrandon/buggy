package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Explicit Owner decision captured as authoritative Decision Memory input. */
public record BuggyOwnerDecisionDraft(
        String title,
        BuggyKnowledgeTopic topic,
        String decision,
        String scope,
        String rationale,
        String authority,
        String sourceRef,
        String sourceLabel,
        BuggyKnowledgeVisibility visibility,
        String conflictKey,
        String conflictValue) {

    public BuggyOwnerDecisionDraft {
        title = CoreValidation.requireText(title, "title", 256);
        topic = CoreValidation.requireNonNull(topic, "topic");
        decision = CoreValidation.requireText(decision, "decision", 8192);
        scope = CoreValidation.requireText(scope, "scope", 1024);
        rationale = CoreValidation.requireText(rationale, "rationale", 8192);
        authority = CoreValidation.requireText(authority, "authority", 512);
        sourceRef = CoreValidation.requireText(sourceRef, "sourceRef", 1024);
        sourceLabel = CoreValidation.optionalText(sourceLabel, "sourceLabel", 256);
        visibility = CoreValidation.requireNonNull(visibility, "visibility");
        conflictKey = CoreValidation.optionalSafeToken(conflictKey, "conflictKey", 128);
        conflictValue = CoreValidation.optionalText(conflictValue, "conflictValue", 1024);
        if (visibility != BuggyKnowledgeVisibility.OWNER_CONFIDENTIAL
                && visibility != BuggyKnowledgeVisibility.CONFIDENTIAL
                && visibility != BuggyKnowledgeVisibility.SECURITY_SENSITIVE) {
            throw new IllegalArgumentException("Owner Decision Memory must be confidential or security-sensitive");
        }
        if ((conflictKey == null) != (conflictValue == null)) {
            throw new IllegalArgumentException("conflictKey and conflictValue must either both be set or both be null");
        }
    }
}
