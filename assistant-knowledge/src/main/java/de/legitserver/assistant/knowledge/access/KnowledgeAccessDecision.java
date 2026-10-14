package de.legitserver.assistant.knowledge.access;

import java.util.Objects;

/** Explicit result of a Knowledge ACL evaluation. */
public record KnowledgeAccessDecision(boolean allowed, KnowledgeAccessDenialReason denialReason) {
    public KnowledgeAccessDecision {
        Objects.requireNonNull(denialReason, "denialReason");
        if (allowed && denialReason != KnowledgeAccessDenialReason.NONE) {
            throw new IllegalArgumentException("Allowed decision must use NONE denial reason");
        }
        if (!allowed && denialReason == KnowledgeAccessDenialReason.NONE) {
            throw new IllegalArgumentException("Denied decision requires a denial reason");
        }
    }

    public static KnowledgeAccessDecision allow() {
        return new KnowledgeAccessDecision(true, KnowledgeAccessDenialReason.NONE);
    }

    public static KnowledgeAccessDecision deny(KnowledgeAccessDenialReason reason) {
        return new KnowledgeAccessDecision(false, reason);
    }
}
