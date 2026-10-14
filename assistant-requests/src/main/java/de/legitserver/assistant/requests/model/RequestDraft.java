package de.legitserver.assistant.requests.model;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.requests.policy.RequestLimits;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Instant;
import java.util.Objects;

/** Validated input for creating a generic Request aggregate. */
public record RequestDraft(
        String requestType,
        RequestRiskLevel riskLevel,
        int requiredApprovals,
        String payloadContentType,
        String payloadBody,
        Instant expiresAt,
        DataClassification classification) {

    public RequestDraft {
        requestType = RequestValidation.requestType(requestType);
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        if (requiredApprovals < 1 || requiredApprovals > RequestLimits.MAX_REQUIRED_APPROVALS) {
            throw new IllegalArgumentException("requiredApprovals must be between 1 and "
                    + RequestLimits.MAX_REQUIRED_APPROVALS);
        }
        payloadContentType = RequestValidation.contentType(payloadContentType);
        payloadBody = RequestValidation.payloadBody(payloadBody);
        classification = Objects.requireNonNull(classification, "classification");
    }
}
