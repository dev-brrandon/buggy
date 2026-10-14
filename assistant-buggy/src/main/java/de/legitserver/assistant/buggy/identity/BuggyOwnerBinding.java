package de.legitserver.assistant.buggy.identity;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Versioned owner binding. Identity authority remains NetworkCore; this is only the persisted binding reference. */
public record BuggyOwnerBinding(
        long revision,
        ActorId ownerActorId,
        String authoritySource,
        ActorId createdBy,
        Instant createdAt,
        String sha256) {

    public static final String AUTHORITY_SOURCE = "NETWORKCORE";

    public BuggyOwnerBinding {
        if (revision < 1) throw new IllegalArgumentException("owner binding revision must be >= 1");
        ownerActorId = CoreValidation.requireNonNull(ownerActorId, "ownerActorId");
        authoritySource = CoreValidation.requireSafeToken(authoritySource, "authoritySource", 64);
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        if (!AUTHORITY_SOURCE.equals(authoritySource)) {
            throw new IllegalArgumentException("Buggy owner authority must remain NetworkCore");
        }
        if (!hash(ownerActorId, authoritySource).equals(sha256)) {
            throw new IllegalArgumentException("Buggy owner binding SHA-256 mismatch");
        }
    }

    public static BuggyOwnerBinding initial(ActorId ownerActorId, ActorId createdBy, Instant createdAt) {
        return new BuggyOwnerBinding(
                1L,
                ownerActorId,
                AUTHORITY_SOURCE,
                createdBy,
                createdAt,
                hash(ownerActorId, AUTHORITY_SOURCE));
    }

    public static String hash(ActorId ownerActorId, String authoritySource) {
        String canonical = "ownerActorId=" + ownerActorId.value() + "\nauthoritySource=" + authoritySource;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
