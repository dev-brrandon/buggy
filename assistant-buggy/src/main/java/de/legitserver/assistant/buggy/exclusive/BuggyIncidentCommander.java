package de.legitserver.assistant.buggy.exclusive;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Produces incident orchestration policy without granting or executing additional authority. */
public final class BuggyIncidentCommander {
    private final Clock clock;

    public BuggyIncidentCommander(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyIncidentCommandPlan prepare(
            String incidentRef,
            BuggyIncidentSeverity severity,
            List<String> diagnosticSteps,
            List<String> recoveryPreparationSteps) {
        Objects.requireNonNull(severity, "severity");
        boolean serious = severity == BuggyIncidentSeverity.SEV1 || severity == BuggyIncidentSeverity.SEV2;
        boolean critical = severity == BuggyIncidentSeverity.SEV1;
        return new BuggyIncidentCommandPlan(
                incidentRef,
                severity,
                serious,
                serious,
                true,
                serious,
                critical,
                diagnosticSteps,
                recoveryPreparationSteps,
                clock.instant());
    }
}
