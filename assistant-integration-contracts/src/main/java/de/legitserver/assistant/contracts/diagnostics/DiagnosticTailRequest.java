package de.legitserver.assistant.contracts.diagnostics;

import java.util.Objects;

public record DiagnosticTailRequest(DiagnosticTarget target, int maxLines) {
    public DiagnosticTailRequest {
        target = Objects.requireNonNull(target, "target");
        if (maxLines < 1 || maxLines > 500) throw new IllegalArgumentException("maxLines must be 1..500");
    }
}
