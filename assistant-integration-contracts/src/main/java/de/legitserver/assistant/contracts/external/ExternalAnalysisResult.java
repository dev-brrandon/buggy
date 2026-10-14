package de.legitserver.assistant.contracts.external;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

public record ExternalAnalysisResult(Status status, String summary, DataClassification classification, String safeCode) {
    public ExternalAnalysisResult {
        status = Objects.requireNonNull(status, "status");
        classification = Objects.requireNonNull(classification, "classification");
        safeCode = ContractValidation.id(safeCode, "safeCode");
        if (status == Status.COMPLETED) summary = ContractValidation.text(summary, "summary", 100_000);
        else if (summary != null) throw new IllegalArgumentException("non-completed result must not carry summary");
    }

    public enum Status { COMPLETED, REJECTED, UNAVAILABLE }

    public static ExternalAnalysisResult unavailable() {
        return new ExternalAnalysisResult(Status.UNAVAILABLE, null, DataClassification.INTERNAL, "INTEGRATION_NOT_CONFIGURED");
    }
}
