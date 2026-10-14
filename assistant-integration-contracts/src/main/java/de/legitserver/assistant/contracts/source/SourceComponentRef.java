package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;

public record SourceComponentRef(String value) {
    public SourceComponentRef {
        value = ContractValidation.id(value, "component");
    }
}
