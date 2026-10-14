package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.util.Map;

public record SourceBuildMetadata(Map<String, String> values) {
    public SourceBuildMetadata {
        values = ContractValidation.metadata(values, 64);
    }
}
