package de.legitserver.assistant.security.control;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe Phase-3 control plane; persistence/admin APIs arrive in later phases. */
public final class InMemorySecurityControlPlane implements SecurityControlPlane {
    private final AtomicLong revision = new AtomicLong();
    private final AtomicReference<SecurityControlState> state;

    public InMemorySecurityControlPlane(Set<SecurityFeature> initiallyEnabled, Instant now) {
        CoreValidation.requireNonNull(now, "now");
        state = new AtomicReference<>(new SecurityControlState(
                OperationalMode.NORMAL,
                initiallyEnabled == null ? Set.of() : Set.copyOf(initiallyEnabled),
                Set.of(),
                revision.incrementAndGet(),
                now));
    }

    @Override
    public SecurityControlState current() {
        return state.get();
    }

    public SecurityControlState setOperationalMode(OperationalMode mode, Instant now) {
        return update(mode, current().enabledFeatures(), current().activeKillSwitches(), now);
    }

    public SecurityControlState setFeature(SecurityFeature feature, boolean enabled, Instant now) {
        CoreValidation.requireNonNull(feature, "feature");
        java.util.EnumSet<SecurityFeature> features = current().enabledFeatures().isEmpty()
                ? java.util.EnumSet.noneOf(SecurityFeature.class)
                : java.util.EnumSet.copyOf(current().enabledFeatures());
        if (enabled) features.add(feature); else features.remove(feature);
        return update(current().operationalMode(), features, current().activeKillSwitches(), now);
    }

    public SecurityControlState setKillSwitch(SecurityKillSwitch killSwitch, boolean active, Instant now) {
        CoreValidation.requireNonNull(killSwitch, "killSwitch");
        java.util.EnumSet<SecurityKillSwitch> switches = current().activeKillSwitches().isEmpty()
                ? java.util.EnumSet.noneOf(SecurityKillSwitch.class)
                : java.util.EnumSet.copyOf(current().activeKillSwitches());
        if (active) switches.add(killSwitch); else switches.remove(killSwitch);
        return update(current().operationalMode(), current().enabledFeatures(), switches, now);
    }

    private SecurityControlState update(
            OperationalMode mode,
            Set<SecurityFeature> features,
            Set<SecurityKillSwitch> switches,
            Instant now) {
        SecurityControlState next = new SecurityControlState(
                CoreValidation.requireNonNull(mode, "mode"),
                features,
                switches,
                revision.incrementAndGet(),
                CoreValidation.requireNonNull(now, "now"));
        state.set(next);
        return next;
    }
}
