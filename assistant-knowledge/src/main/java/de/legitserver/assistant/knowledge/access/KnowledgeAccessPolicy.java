package de.legitserver.assistant.knowledge.access;

import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import java.util.Objects;

/** Canonical Phase-7 ACL policy: authority + domain + classification + document permissions. */
public final class KnowledgeAccessPolicy {
    public KnowledgeAccessDecision evaluate(KnowledgeDocument document, KnowledgeAccessContext context) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(context, "context");
        if (!context.queryAuthorized()) {
            return KnowledgeAccessDecision.deny(KnowledgeAccessDenialReason.QUERY_NOT_AUTHORIZED);
        }
        if (!context.allowedDomains().contains(document.domain())) {
            return KnowledgeAccessDecision.deny(KnowledgeAccessDenialReason.DOMAIN_DENIED);
        }
        if (!context.maximumClassification().isAtLeast(document.classification())) {
            return KnowledgeAccessDecision.deny(KnowledgeAccessDenialReason.CLASSIFICATION_DENIED);
        }
        if (!context.effectivePermissions().containsAll(document.acl().requiredPermissions())) {
            return KnowledgeAccessDecision.deny(KnowledgeAccessDenialReason.DOCUMENT_PERMISSION_DENIED);
        }
        return KnowledgeAccessDecision.allow();
    }
}
