package de.legitserver.assistant.buggy.selfdev;

import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.util.Objects;

/** Exact Foundation Request binding. Payload hash prevents approval replay across proposal revisions. */
public record BuggySelfDevelopmentApprovalBinding(
        ApprovalRequestId requestId,
        long submittedItemRevision,
        String requestType,
        String payloadSha256,
        ToolRiskLevel riskLevel) {
    public BuggySelfDevelopmentApprovalBinding {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (submittedItemRevision < 1) throw new IllegalArgumentException("submittedItemRevision must be >= 1");
        requestType = CoreValidation.requireSafeToken(requestType, "requestType", 128);
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be 64 lowercase hex characters");
        }
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
    }
}
