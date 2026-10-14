package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import java.util.List;
import java.util.Optional;

/** Persistence port for B2 append-only Buggy Personality state and observation history. */
public interface BuggyPersonalityRepository {
    Optional<BuggyPersonalitySnapshot> find();

    BuggyPersonalitySnapshot initialize(BuggyPersonalitySeed seed, BuggyPersonalityRevision initialRevision);

    Optional<BuggyPersonalityRevision> findRevision(long revision);

    Optional<BuggyPersonalityObservation> findObservation(String observationId);

    List<BuggyPersonalityRevision> history();

    List<BuggyPersonalityObservation> observations();

    BuggyPersonalityObservation appendObservation(BuggyPersonalityObservation observation);

    BuggyPersonalitySnapshot appendRevision(long expectedCurrentRevision, BuggyPersonalityRevision revision);
}
