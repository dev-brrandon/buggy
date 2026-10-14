package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

public record SourceFileEntry(
        SourcePath path,
        long sizeBytes,
        String checksum,
        DataClassification classification) {
    public SourceFileEntry {
        path = Objects.requireNonNull(path, "path");
        if (path.value().isEmpty()) throw new IllegalArgumentException("file path must not be root");
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be >= 0");
        checksum = ContractValidation.sha256(checksum);
        classification = Objects.requireNonNull(classification, "classification");
    }
}
