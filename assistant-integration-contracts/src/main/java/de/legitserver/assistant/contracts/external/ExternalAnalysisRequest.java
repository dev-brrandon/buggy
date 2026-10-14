package de.legitserver.assistant.contracts.external;

import de.legitserver.assistant.contracts.common.ApprovalBinding;
import de.legitserver.assistant.contracts.common.ContractValidation;
import java.util.Objects;

/** External analysis may cross a trust boundary only with exact approval and sanitized manifested payload. */
public record ExternalAnalysisRequest(
        String analysisType,
        ExternalAnalysisPayload payload,
        ApprovalBinding approvalBinding) {
    public ExternalAnalysisRequest {
        analysisType = ContractValidation.id(analysisType, "analysisType");
        payload = Objects.requireNonNull(payload, "payload");
        approvalBinding = Objects.requireNonNull(approvalBinding, "approvalBinding");
        String expected = ExternalAnalysisApprovalCanonicalizer.payloadSha256(analysisType, payload.manifest());
        if (!expected.equals(approvalBinding.payloadSha256())) {
            throw new IllegalArgumentException("approval binding does not match exact external-analysis manifest");
        }
    }
}
