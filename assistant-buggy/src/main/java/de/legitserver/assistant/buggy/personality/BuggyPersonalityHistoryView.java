package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

/** Read model used later by the B8 Control Center backend without exposing mutation authority. */
public record BuggyPersonalityHistoryView(
        BuggyPersonalitySeed seed,
        BuggyPersonalityRevision current,
        List<BuggyPersonalityRevision> revisions,
        List<BuggyPersonalityObservation> observations) {

    public BuggyPersonalityHistoryView {
        seed = CoreValidation.requireNonNull(seed, "seed");
        current = CoreValidation.requireNonNull(current, "current");
        revisions = List.copyOf(CoreValidation.requireNonNull(revisions, "revisions"));
        observations = List.copyOf(CoreValidation.requireNonNull(observations, "observations"));
    }
}
