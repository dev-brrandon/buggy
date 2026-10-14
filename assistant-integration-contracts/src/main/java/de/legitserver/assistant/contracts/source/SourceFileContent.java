package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Text-only source content bound to a snapshot-relative path and checksum. */
public record SourceFileContent(
        SourcePath path,
        String content,
        String checksum,
        DataClassification classification) {
    public SourceFileContent {
        path = Objects.requireNonNull(path, "path");
        if (path.value().isEmpty()) throw new IllegalArgumentException("file path must not be root");
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (content.getBytes(StandardCharsets.UTF_8).length > 2_000_000) {
            throw new IllegalArgumentException("content exceeds contract limit");
        }
        checksum = ContractValidation.sha256(checksum);
        if (!checksum.equals(ContractValidation.sha256Of(content))) {
            throw new IllegalArgumentException("checksum does not match content");
        }
        classification = Objects.requireNonNull(classification, "classification");
    }
}
