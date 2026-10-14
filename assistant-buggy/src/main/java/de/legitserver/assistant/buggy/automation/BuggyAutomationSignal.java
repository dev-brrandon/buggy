package de.legitserver.assistant.buggy.automation;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Sanitized signal envelope. Integrations keep raw logs/metrics outside this product record. */
public record BuggyAutomationSignal(
        BuggySignalType type,
        String targetFingerprint,
        boolean healthy,
        Map<String, String> evidence,
        ActorContext actor,
        Instant observedAt) {
    public BuggyAutomationSignal {
        type = Objects.requireNonNull(type, "type");
        targetFingerprint = CoreValidation.requireSafeToken(targetFingerprint, "targetFingerprint", 192);
        actor = Objects.requireNonNull(actor, "actor");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (evidence != null) {
            if (evidence.size() > 32) throw new IllegalArgumentException("evidence exceeds 32 entries");
            for (Map.Entry<String, String> item : evidence.entrySet()) {
                normalized.put(CoreValidation.requireSafeToken(item.getKey(), "evidenceKey", 64),
                        CoreValidation.requireText(item.getValue(), "evidenceValue", 1024));
            }
        }
        evidence = Map.copyOf(normalized);
    }
}
