package de.legitserver.assistant.buggy.exclusive;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Composes already-authorized diagnostic evidence; execution remains exclusively in the B5/Foundation runtime. */
public final class BuggyDeepInvestigationService {
    private final Clock clock;

    public BuggyDeepInvestigationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyDeepInvestigationReport complete(
            String investigationId,
            BuggyContextRef context,
            BuggyCorrelationReport correlation,
            BuggyHistoricalComparison historicalComparison,
            BuggyArchitectureReview architectureReview,
            List<String> unresolvedQuestions,
            List<String> recommendedNextChecks) {
        Objects.requireNonNull(correlation, "correlation");
        if (correlation.evidence().isEmpty()) {
            throw new IllegalArgumentException("deep investigation requires evidence");
        }
        return new BuggyDeepInvestigationReport(
                investigationId,
                context,
                correlation,
                historicalComparison,
                architectureReview,
                unresolvedQuestions,
                recommendedNextChecks,
                clock.instant());
    }
}
