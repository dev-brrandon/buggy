package de.legitserver.assistant.contracts.inference;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

public record InferenceResult(Status status, String text, DataClassification classification, String safeCode) {
    public InferenceResult {
        status = Objects.requireNonNull(status, "status");
        classification = Objects.requireNonNull(classification, "classification");
        safeCode = ContractValidation.id(safeCode, "safeCode");
        if (status == Status.COMPLETED) text = ContractValidation.text(text, "text", 100_000);
        else if (text != null) throw new IllegalArgumentException("non-completed inference must not carry text");
    }

    public enum Status { COMPLETED, REJECTED, UNAVAILABLE }

    public static InferenceResult unavailable() {
        return new InferenceResult(Status.UNAVAILABLE, null, DataClassification.INTERNAL, "INTEGRATION_NOT_CONFIGURED");
    }
}
