package de.legitserver.assistant.contracts.external;

import java.util.List;

/** Sanitized payload and its deterministic manifest. */
public record ExternalAnalysisPayload(List<ExternalPayloadItem> items, PayloadManifest manifest) {
    public ExternalAnalysisPayload {
        if (items == null || items.isEmpty() || items.size() > 32) throw new IllegalArgumentException("items must contain 1..32 entries");
        items = List.copyOf(items);
        PayloadManifest expected = PayloadManifest.from(items);
        if (manifest == null) manifest = expected;
        if (!expected.equals(manifest)) throw new IllegalArgumentException("manifest does not match exact payload");
    }

    public static ExternalAnalysisPayload of(List<ExternalPayloadItem> items) {
        return new ExternalAnalysisPayload(items, null);
    }
}
