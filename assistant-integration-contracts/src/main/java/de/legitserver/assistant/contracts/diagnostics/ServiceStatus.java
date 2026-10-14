package de.legitserver.assistant.contracts.diagnostics;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.time.Instant;
import java.util.Objects;

public record ServiceStatus(String state, String detail, Instant observedAt) {
    public ServiceStatus {
        state = ContractValidation.text(state, "state", 80);
        detail = ContractValidation.optionalText(detail, "detail", 500);
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
}
