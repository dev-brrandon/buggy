package de.legitserver.assistant.contracts.diagnostics;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.time.Instant;
import java.util.Objects;

public record ContainerStatus(String state, String health, Instant observedAt) {
    public ContainerStatus {
        state = ContractValidation.text(state, "state", 80);
        health = ContractValidation.optionalText(health, "health", 160);
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
}
