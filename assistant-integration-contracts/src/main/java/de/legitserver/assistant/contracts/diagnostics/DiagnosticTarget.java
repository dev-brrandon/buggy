package de.legitserver.assistant.contracts.diagnostics;

import de.legitserver.assistant.contracts.common.ContractValidation;

public record DiagnosticTarget(String type, String id) {
    public DiagnosticTarget {
        type = ContractValidation.id(type, "type");
        id = ContractValidation.id(id, "id");
    }
}
