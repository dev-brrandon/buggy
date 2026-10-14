package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

public record SourceSearchHit(
        SourcePath path,
        int line,
        String excerpt,
        DataClassification classification) {
    public SourceSearchHit {
        path = Objects.requireNonNull(path, "path");
        if (path.value().isEmpty()) throw new IllegalArgumentException("file path must not be root");
        if (line < 1) throw new IllegalArgumentException("line must be >= 1");
        excerpt = ContractValidation.text(excerpt, "excerpt", 2000);
        classification = Objects.requireNonNull(classification, "classification");
    }
}
