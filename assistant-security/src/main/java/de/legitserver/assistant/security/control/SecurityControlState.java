package de.legitserver.assistant.security.control;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Set;

/** Immutable runtime security-control snapshot. */
public record SecurityControlState(
        OperationalMode operationalMode,
        Set<SecurityFeature> enabledFeatures,
        Set<SecurityKillSwitch> activeKillSwitches,
        long revision,
        Instant changedAt) {

    public SecurityControlState {
        operationalMode = CoreValidation.requireNonNull(operationalMode, "operationalMode");
        enabledFeatures = enabledFeatures == null ? Set.of() : Set.copyOf(enabledFeatures);
        activeKillSwitches = activeKillSwitches == null ? Set.of() : Set.copyOf(activeKillSwitches);
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
        changedAt = CoreValidation.requireNonNull(changedAt, "changedAt");
    }

    public boolean featureEnabled(SecurityFeature feature) {
        return enabledFeatures.contains(CoreValidation.requireNonNull(feature, "feature"));
    }

    public boolean killSwitchActive(SecurityKillSwitch killSwitch) {
        return activeKillSwitches.contains(CoreValidation.requireNonNull(killSwitch, "killSwitch"));
    }
}
