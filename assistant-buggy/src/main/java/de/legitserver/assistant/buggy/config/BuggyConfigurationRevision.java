package de.legitserver.assistant.buggy.config;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Immutable, versioned Buggy configuration revision. */
public record BuggyConfigurationRevision(
        long revision,
        BuggyManifest manifest,
        ActorId createdBy,
        Instant createdAt,
        String sha256) {

    public BuggyConfigurationRevision {
        if (revision < 1) throw new IllegalArgumentException("configuration revision must be >= 1");
        manifest = CoreValidation.requireNonNull(manifest, "manifest");
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        if (!BuggyManifestCanonicalizer.sha256(manifest).equals(sha256)) {
            throw new IllegalArgumentException("Buggy configuration SHA-256 mismatch");
        }
    }

    public static BuggyConfigurationRevision initial(BuggyManifest manifest, ActorId createdBy, Instant createdAt) {
        return new BuggyConfigurationRevision(
                1L, manifest, createdBy, createdAt, BuggyManifestCanonicalizer.sha256(manifest));
    }
}
