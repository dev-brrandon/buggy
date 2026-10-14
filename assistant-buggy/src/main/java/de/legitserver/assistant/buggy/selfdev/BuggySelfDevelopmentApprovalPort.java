package de.legitserver.assistant.buggy.selfdev;

import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;

/** B7 port backed by the existing Foundation Requests / Approvals service. */
public interface BuggySelfDevelopmentApprovalPort {
    String PROMOTION_REQUEST_TYPE = "BUGGY_SELF_DEVELOPMENT_PROMOTION";
    String ROLLBACK_REQUEST_TYPE = "BUGGY_SELF_DEVELOPMENT_ROLLBACK";

    BuggySelfDevelopmentApprovalBinding submitPromotion(
            BuggySelfDevelopmentPromotionPackage promotionPackage,
            RequestContext context);

    BuggySelfDevelopmentApprovalBinding submitRollback(
            BuggySelfDevelopmentId itemId,
            long submittedRevision,
            String deploymentReference,
            ToolRiskLevel riskLevel,
            RequestContext context);

    BuggySelfDevelopmentApprovalStatus status(BuggySelfDevelopmentApprovalBinding binding);
}
