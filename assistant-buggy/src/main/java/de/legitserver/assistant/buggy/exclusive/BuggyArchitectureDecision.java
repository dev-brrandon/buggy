package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured projection of a canonical architecture/Owner decision from Knowledge/Decision Memory. */
public record BuggyArchitectureDecision(
        String decisionId,
        String scope,
        String rationale,
        Map<String, String> requiredProperties,
        Map<String, String> forbiddenProperties) {
    public BuggyArchitectureDecision {
        decisionId = CoreValidation.requireSafeToken(decisionId, "decisionId", 160);
        scope = CoreValidation.requireSafeToken(scope, "scope", 160);
        rationale = CoreValidation.requireText(rationale, "rationale", 2048);
        requiredProperties = checked(requiredProperties, "requiredProperties");
        forbiddenProperties = checked(forbiddenProperties, "forbiddenProperties");
    }

    private static Map<String, String> checked(Map<String, String> input, String field) {
        if (input == null) throw new IllegalArgumentException(field + " must not be null");
        if (input.size() > 64) throw new IllegalArgumentException(field + " exceeds 64 entries");
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        input.forEach((key, value) -> out.put(
                CoreValidation.requireSafeToken(key, field + "Key", 128),
                CoreValidation.requireText(value, field + "Value", 1024)));
        return Map.copyOf(out);
    }
}
