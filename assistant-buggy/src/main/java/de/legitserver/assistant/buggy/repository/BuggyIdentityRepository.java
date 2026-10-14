package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for the Buggy identity aggregate. Implementation belongs to assistant-integrations. */
public interface BuggyIdentityRepository {
    Optional<BuggyIdentitySnapshot> find();

    /** Creates the canonical B1 identity exactly once. Existing state is never overwritten. */
    BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed);

    /**
     * Appends a versioned configuration revision activating the B2 personality profile.
     * This is product configuration only and cannot mutate Foundation authority/security fields.
     */
    BuggyIdentitySnapshot activatePersonalityProfile(String profileId, ActorId createdBy, Instant createdAt);
}
