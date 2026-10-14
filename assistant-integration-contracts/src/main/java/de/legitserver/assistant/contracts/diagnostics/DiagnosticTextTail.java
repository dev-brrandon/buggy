package de.legitserver.assistant.contracts.diagnostics;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.util.List;

public record DiagnosticTextTail(List<String> lines, boolean truncated) {
    public DiagnosticTextTail {
        if (lines == null) lines = List.of();
        if (lines.size() > 500) throw new IllegalArgumentException("at most 500 lines are allowed");
        lines = lines.stream().map(line -> ContractValidation.text(line, "diagnostic line", 8000)).toList();
    }
}
