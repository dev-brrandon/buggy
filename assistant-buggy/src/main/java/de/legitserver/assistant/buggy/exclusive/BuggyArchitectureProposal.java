package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.Map;

public record BuggyArchitectureProposal(
        String proposalId,
        String scope,
        String summary,
        Map<String, String> properties) {
    public BuggyArchitectureProposal {
        proposalId = CoreValidation.requireSafeToken(proposalId, "proposalId", 160);
        scope = CoreValidation.requireSafeToken(scope, "scope", 160);
        summary = CoreValidation.requireText(summary, "summary", 2048);
        if (properties == null || properties.isEmpty()) throw new IllegalArgumentException("properties must not be empty");
        if (properties.size() > 128) throw new IllegalArgumentException("properties exceeds 128 entries");
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        properties.forEach((key, value) -> out.put(
                CoreValidation.requireSafeToken(key, "propertyKey", 128),
                CoreValidation.requireText(value, "propertyValue", 1024)));
        properties = Map.copyOf(out);
    }
}
