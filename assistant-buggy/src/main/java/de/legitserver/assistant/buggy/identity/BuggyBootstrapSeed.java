package de.legitserver.assistant.buggy.identity;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Explicit provisioning payload. No public API or tool is exposed for changing this binding in B1. */
public record BuggyBootstrapSeed(
        BuggyIdentityCore identityCore,
        BuggyOwnerBinding ownerBinding,
        BuggyConfigurationRevision configuration) {

    public BuggyBootstrapSeed {
        identityCore = CoreValidation.requireNonNull(identityCore, "identityCore");
        ownerBinding = CoreValidation.requireNonNull(ownerBinding, "ownerBinding");
        configuration = CoreValidation.requireNonNull(configuration, "configuration");
    }

    public static BuggyBootstrapSeed create(ActorId ownerActorId, ActorId provisionedBy, Instant now) {
        CoreValidation.requireNonNull(ownerActorId, "ownerActorId");
        CoreValidation.requireNonNull(provisionedBy, "provisionedBy");
        CoreValidation.requireNonNull(now, "now");
        BuggyManifest manifest = BuggyManifest.initial();
        return new BuggyBootstrapSeed(
                BuggyIdentityCore.canonical(now),
                BuggyOwnerBinding.initial(ownerActorId, provisionedBy, now),
                BuggyConfigurationRevision.initial(manifest, provisionedBy, now));
    }
}
