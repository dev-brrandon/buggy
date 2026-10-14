package de.legitserver.assistant.contracts.external;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One explicitly sanitized export item. */
public record ExternalPayloadItem(
        String name,
        String contentType,
        String content,
        DataClassification classification,
        boolean sanitized) {
    public ExternalPayloadItem {
        name = ContractValidation.id(name, "name");
        contentType = ContractValidation.contentType(contentType);
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (content.getBytes(StandardCharsets.UTF_8).length > 1_000_000) throw new IllegalArgumentException("external payload item exceeds 1MB");
        classification = Objects.requireNonNull(classification, "classification");
        if (!sanitized) throw new IllegalArgumentException("external payload must be sanitized before crossing the port");
    }
}
