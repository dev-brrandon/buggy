package de.legitserver.assistant.contracts.common;

import de.legitserver.assistant.requests.model.ApprovalRequestId;
import java.util.Objects;

/** Exact Phase-9 approval payload binding carried by future mutating/external integration calls. */
public record ApprovalBinding(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
    public ApprovalBinding {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (payloadRevision < 1) throw new IllegalArgumentException("payloadRevision must be >= 1");
        payloadSha256 = ContractValidation.sha256(payloadSha256);
    }
}
