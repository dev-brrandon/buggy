package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Evidence is assumed to have passed normal Foundation authorization before entering B10. */
public record BuggyCorrelationEvidence(
        String evidenceId,
        BuggyEvidenceSource source,
        String subject,
        int confidenceBps,
        Map<String, String> facts,
        Instant observedAt) {
    public BuggyCorrelationEvidence {
        evidenceId = CoreValidation.requireSafeToken(evidenceId, "evidenceId", 160);
        source = CoreValidation.requireNonNull(source, "source");
        subject = CoreValidation.requireText(subject, "subject", 512);
        if (confidenceBps < 0 || confidenceBps > 10_000) throw new IllegalArgumentException("confidenceBps out of range");
        observedAt = CoreValidation.requireNonNull(observedAt, "observedAt");
        LinkedHashMap<String, String> checked = new LinkedHashMap<>();
        if (facts == null || facts.isEmpty()) throw new IllegalArgumentException("facts must not be empty");
        if (facts.size() > 32) throw new IllegalArgumentException("facts exceeds 32 entries");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            String key = CoreValidation.requireSafeToken(entry.getKey(), "factKey", 96);
            String value = CoreValidation.requireText(entry.getValue(), "factValue", 2048);
            checked.put(key, value);
        }
        facts = Map.copyOf(checked);
    }
}
