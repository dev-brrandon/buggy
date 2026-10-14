package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import java.util.Set;

/** Canonical B4 Buggy Knowledge profile. This is a product binding, not an authorization source. */
public final class BuggyKnowledgeProfile {
    public static final String PROFILE_ID = "buggy-knowledge-v1";
    public static final String KNOWLEDGE_READ_PERMISSION = "networkcore.assistant.buggy.knowledge.read";
    public static final String SECURITY_READ_PERMISSION = "networkcore.assistant.buggy.security.read";

    private static final Set<KnowledgeDomain> ALL_FOUNDATION_DOMAINS = Set.of(KnowledgeDomain.values());

    private BuggyKnowledgeProfile() {}

    public static Set<KnowledgeDomain> allowedFoundationDomains() {
        return ALL_FOUNDATION_DOMAINS;
    }

    public static DataClassification maximumNormalClassification() {
        return DataClassification.RESTRICTED;
    }
}
