package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

/** Orchestration plan only; execution still uses B5 tools and Foundation approval/security. */
public record BuggyIncidentCommandPlan(
        String incidentRef,
        BuggyIncidentSeverity severity,
        boolean reduceSideActivities,
        boolean increaseDiagnostics,
        boolean maintainTimeline,
        boolean createOrUpdateBugRecord,
        boolean ownerEscalationRequired,
        List<String> diagnosticSteps,
        List<String> recoveryPreparationSteps,
        Instant preparedAt) {
    public BuggyIncidentCommandPlan {
        incidentRef = CoreValidation.requireSafeToken(incidentRef, "incidentRef", 200);
        severity = CoreValidation.requireNonNull(severity, "severity");
        diagnosticSteps = checked(diagnosticSteps, "diagnosticSteps");
        recoveryPreparationSteps = checked(recoveryPreparationSteps, "recoveryPreparationSteps");
        preparedAt = CoreValidation.requireNonNull(preparedAt, "preparedAt");
    }

    private static List<String> checked(List<String> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        if (values.size() > 64) throw new IllegalArgumentException(field + " exceeds 64 entries");
        return values.stream().map(value -> CoreValidation.requireText(value, field, 1024)).toList();
    }
}
