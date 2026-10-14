package de.legitserver.assistant.buggy.identity;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Consistent persistent Buggy identity snapshot loaded from the Assistant-owned persistence boundary. */
public record BuggyIdentitySnapshot(
        BuggyIdentityCore identityCore,
        BuggyOwnerBinding ownerBinding,
        BuggyConfigurationRevision configuration,
        long headRevision,
        Instant updatedAt) {

    public BuggyIdentitySnapshot {
        identityCore = CoreValidation.requireNonNull(identityCore, "identityCore");
        ownerBinding = CoreValidation.requireNonNull(ownerBinding, "ownerBinding");
        configuration = CoreValidation.requireNonNull(configuration, "configuration");
        if (headRevision < 1) throw new IllegalArgumentException("headRevision must be >= 1");
        updatedAt = CoreValidation.requireNonNull(updatedAt, "updatedAt");
    }
}
