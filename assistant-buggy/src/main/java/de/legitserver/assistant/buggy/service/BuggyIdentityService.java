package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import java.time.Instant;
import java.util.Objects;

/** Product service for loading/provisioning the persistent Buggy identity without becoming a security authority. */
public final class BuggyIdentityService {
    private final BuggyIdentityRepository repository;

    public BuggyIdentityService(BuggyIdentityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public BuggyIdentitySnapshot loadRequired() {
        return repository.find().orElseThrow(BuggyIdentityUnavailableException::new);
    }

    public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
        return repository.initialize(Objects.requireNonNull(seed, "seed"));
    }

    public BuggyIdentitySnapshot activatePersonalityProfile(
            String profileId, ActorId createdBy, Instant createdAt) {
        return repository.activatePersonalityProfile(
                Objects.requireNonNull(profileId, "profileId"),
                Objects.requireNonNull(createdBy, "createdBy"),
                Objects.requireNonNull(createdAt, "createdAt"));
    }

    public AssistantDescriptor assistantDescriptor() {
        BuggyIdentitySnapshot snapshot = loadRequired();
        var manifest = snapshot.configuration().manifest();
        return new AssistantDescriptor(
                manifest.assistantId(),
                manifest.displayName(),
                true,
                manifest.capabilityProfileId(),
                manifest.policyProfileId(),
                manifest.knowledgeProfileId(),
                manifest.runtimeProfileId(),
                manifest.personalityProfileId(),
                snapshot.headRevision(),
                snapshot.updatedAt());
    }
}
