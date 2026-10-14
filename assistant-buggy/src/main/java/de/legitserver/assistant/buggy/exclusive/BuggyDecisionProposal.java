package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

public record BuggyDecisionProposal(
        String decisionId,
        String statement,
        BuggyDecisionSignificance significance,
        List<String> assumptions) {
    public BuggyDecisionProposal {
        decisionId = CoreValidation.requireSafeToken(decisionId, "decisionId", 160);
        statement = CoreValidation.requireText(statement, "statement", 4096);
        significance = CoreValidation.requireNonNull(significance, "significance");
        if (assumptions == null) throw new IllegalArgumentException("assumptions required");
        assumptions = assumptions.stream().map(value -> CoreValidation.requireText(value, "assumption", 1024)).toList();
    }
}
