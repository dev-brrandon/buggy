package de.legitserver.assistant.contracts.write;

import de.legitserver.assistant.contracts.common.ApprovalBinding;
import de.legitserver.assistant.contracts.common.ContractValidation;
import java.util.Objects;

/** Future controlled write request. Exact Phase-9 approval binding is mandatory and payload-bound. */
public record ControlledWriteRequest(
        String idempotencyKey,
        ControlledWriteCommand command,
        ApprovalBinding approvalBinding) {
    public ControlledWriteRequest {
        idempotencyKey = ContractValidation.id(idempotencyKey, "idempotencyKey");
        command = Objects.requireNonNull(command, "command");
        approvalBinding = Objects.requireNonNull(approvalBinding, "approvalBinding");
        String expected = ControlledWriteApprovalCanonicalizer.payloadSha256(idempotencyKey, command);
        if (!expected.equals(approvalBinding.payloadSha256())) {
            throw new IllegalArgumentException("approval binding does not match exact controlled-write payload");
        }
    }
}
