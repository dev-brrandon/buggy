package de.legitserver.assistant.contracts.write;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.util.Objects;

public record ControlledWriteResult(Status status, String safeCode) {
    public ControlledWriteResult {
        status = Objects.requireNonNull(status, "status");
        safeCode = ContractValidation.id(safeCode, "safeCode");
    }

    public enum Status { APPLIED, REJECTED, CONFLICT, NOT_CONFIGURED }

    public static ControlledWriteResult notConfigured() {
        return new ControlledWriteResult(Status.NOT_CONFIGURED, "INTEGRATION_NOT_CONFIGURED");
    }
}
