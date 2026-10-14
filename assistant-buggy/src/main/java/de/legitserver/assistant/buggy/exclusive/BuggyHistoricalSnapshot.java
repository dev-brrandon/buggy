package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record BuggyHistoricalSnapshot(
        String snapshotId,
        String subject,
        Instant capturedAt,
        Map<String, String> attributes) {
    public BuggyHistoricalSnapshot {
        snapshotId = CoreValidation.requireSafeToken(snapshotId, "snapshotId", 160);
        subject = CoreValidation.requireText(subject, "subject", 512);
        capturedAt = CoreValidation.requireNonNull(capturedAt, "capturedAt");
        if (attributes == null || attributes.isEmpty()) throw new IllegalArgumentException("attributes must not be empty");
        if (attributes.size() > 256) throw new IllegalArgumentException("attributes exceeds 256 entries");
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        attributes.forEach((key, value) -> out.put(
                CoreValidation.requireSafeToken(key, "attributeKey", 128),
                CoreValidation.requireText(value, "attributeValue", 2048)));
        attributes = Map.copyOf(out);
    }
}
