package de.legitserver.assistant.contracts.diagnostics;

import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;
import java.util.Optional;

/** Sanitized diagnostic result. No raw command/process handle can cross this contract. */
public record DiagnosticResult<T>(
        DiagnosticStatus status,
        T value,
        DataClassification classification,
        boolean sanitized) {
    public DiagnosticResult {
        status = Objects.requireNonNull(status, "status");
        classification = Objects.requireNonNull(classification, "classification");
        if (status == DiagnosticStatus.FOUND && value == null) throw new IllegalArgumentException("FOUND requires value");
        if (status != DiagnosticStatus.FOUND && value != null) throw new IllegalArgumentException("non-FOUND must not carry value");
        if (status == DiagnosticStatus.FOUND && !sanitized) throw new IllegalArgumentException("diagnostic output must be sanitized");
    }

    public Optional<T> optionalValue() { return Optional.ofNullable(value); }

    public static <T> DiagnosticResult<T> unavailable() {
        return new DiagnosticResult<>(DiagnosticStatus.UNAVAILABLE, null, DataClassification.INTERNAL, true);
    }
}
