package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

/** Read/diagnostic-only investigation product. It contains no execution authority. */
public record BuggyDeepInvestigationReport(
        String investigationId,
        BuggyContextRef context,
        BuggyCorrelationReport correlation,
        BuggyHistoricalComparison historicalComparison,
        BuggyArchitectureReview architectureReview,
        List<String> unresolvedQuestions,
        List<String> recommendedNextChecks,
        Instant completedAt) {
    public BuggyDeepInvestigationReport {
        investigationId = CoreValidation.requireSafeToken(investigationId, "investigationId", 160);
        context = CoreValidation.requireNonNull(context, "context");
        correlation = CoreValidation.requireNonNull(correlation, "correlation");
        unresolvedQuestions = checked(unresolvedQuestions, "unresolvedQuestion");
        recommendedNextChecks = checked(recommendedNextChecks, "recommendedNextCheck");
        completedAt = CoreValidation.requireNonNull(completedAt, "completedAt");
    }

    private static List<String> checked(List<String> values, String field) {
        if (values == null) throw new IllegalArgumentException(field + " list required");
        if (values.size() > 64) throw new IllegalArgumentException(field + " list exceeds 64 entries");
        return values.stream().map(value -> CoreValidation.requireText(value, field, 2048)).toList();
    }
}
