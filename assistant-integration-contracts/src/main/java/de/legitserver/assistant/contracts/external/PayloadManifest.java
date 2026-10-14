package de.legitserver.assistant.contracts.external;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Hash/size/classification manifest for every outbound sanitized payload item. */
public record PayloadManifest(
        int schemaVersion,
        List<Entry> entries,
        long totalBytes,
        String aggregateSha256) {
    public PayloadManifest {
        if (schemaVersion != 1) throw new IllegalArgumentException("unsupported manifest schemaVersion");
        if (entries == null || entries.isEmpty() || entries.size() > 32) throw new IllegalArgumentException("manifest must contain 1..32 entries");
        entries = List.copyOf(entries);
        if (totalBytes < 0 || totalBytes > 4_000_000) throw new IllegalArgumentException("manifest totalBytes exceeds contract limit");
        aggregateSha256 = ContractValidation.sha256(aggregateSha256);
    }

    public static PayloadManifest from(List<ExternalPayloadItem> items) {
        if (items == null || items.isEmpty() || items.size() > 32) throw new IllegalArgumentException("items must contain 1..32 entries");
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        for (ExternalPayloadItem item : items) {
            Objects.requireNonNull(item, "item");
            byte[] bytes = item.content().getBytes(StandardCharsets.UTF_8);
            total = Math.addExact(total, bytes.length);
            entries.add(new Entry(item.name(), item.contentType(), bytes.length, ContractValidation.sha256Of(item.content()), item.classification(), true));
        }
        if (total > 4_000_000) throw new IllegalArgumentException("external payload exceeds 4MB");
        List<Entry> ordered = entries.stream().sorted(Comparator.comparing(Entry::name)).toList();
        long uniqueNames = ordered.stream().map(Entry::name).distinct().count();
        if (uniqueNames != ordered.size()) throw new IllegalArgumentException("external payload item names must be unique");
        String canonical = ordered.stream()
                .map(entry -> entry.name() + "|" + entry.contentType() + "|" + entry.sizeBytes() + "|" + entry.sha256() + "|" + entry.classification().name() + "|1")
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        return new PayloadManifest(1, ordered, total, ContractValidation.sha256Of(canonical));
    }

    public record Entry(
            String name,
            String contentType,
            long sizeBytes,
            String sha256,
            DataClassification classification,
            boolean sanitized) {
        public Entry {
            name = ContractValidation.id(name, "name");
            contentType = ContractValidation.contentType(contentType);
            if (sizeBytes < 0 || sizeBytes > 1_000_000) throw new IllegalArgumentException("sizeBytes outside contract limit");
            sha256 = ContractValidation.sha256(sha256);
            classification = Objects.requireNonNull(classification, "classification");
            if (!sanitized) throw new IllegalArgumentException("manifest entry must be sanitized");
        }
    }
}
