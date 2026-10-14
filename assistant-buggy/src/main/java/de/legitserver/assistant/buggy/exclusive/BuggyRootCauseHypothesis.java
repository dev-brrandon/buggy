package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Evidence-backed hypothesis. It is never presented as certainty solely because Buggy generated it. */
public record BuggyRootCauseHypothesis(
        String hypothesisId,
        String statement,
        int confidenceBps,
        List<String> supportingEvidenceIds,
        List<String> contradictingEvidenceIds) {
    public BuggyRootCauseHypothesis {
        hypothesisId = CoreValidation.requireSafeToken(hypothesisId, "hypothesisId", 160);
        statement = CoreValidation.requireText(statement, "statement", 2048);
        if (confidenceBps < 0 || confidenceBps > 10_000) throw new IllegalArgumentException("confidenceBps out of range");
        supportingEvidenceIds = validateIds(supportingEvidenceIds, "supportingEvidenceIds", true);
        contradictingEvidenceIds = validateIds(contradictingEvidenceIds, "contradictingEvidenceIds", false);
        Set<String> overlap = new HashSet<>(supportingEvidenceIds);
        overlap.retainAll(contradictingEvidenceIds);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("evidence cannot both support and contradict a hypothesis");
    }

    private static List<String> validateIds(List<String> values, String field, boolean required) {
        if (values == null) throw new IllegalArgumentException(field + " must not be null");
        List<String> copy = values.stream()
                .map(value -> CoreValidation.requireSafeToken(value, field, 160))
                .distinct()
                .toList();
        if (required && copy.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        if (copy.size() > 64) throw new IllegalArgumentException(field + " exceeds 64 entries");
        return copy;
    }
}
