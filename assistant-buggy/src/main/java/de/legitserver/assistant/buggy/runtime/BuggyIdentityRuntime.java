package de.legitserver.assistant.buggy.runtime;

import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime projection of the persistent Buggy identity. Restarting this object creates a new runtime instance while
 * preserving the canonical identity and persisted configuration/owner binding.
 */
public final class BuggyIdentityRuntime {
    private final BuggyIdentitySnapshot identity;
    private final BuggyRuntimeInstance runtimeInstance;
    private final Clock clock;

    public BuggyIdentityRuntime(BuggyIdentityService identities, Clock clock) {
        Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identity = identities.loadRequired();
        var configuration = identity.configuration();
        this.runtimeInstance = new BuggyRuntimeInstance(
                UUID.randomUUID(),
                identity.identityCore().assistantId(),
                identity.headRevision(),
                configuration.revision(),
                configuration.manifest().runtimeProfileId(),
                clock.instant());
    }

    public BuggyIdentitySnapshot identity() {
        return identity;
    }

    public BuggyRuntimeInstance runtimeInstance() {
        return runtimeInstance;
    }

    public BuggySessionContext openSession(UUID foundationConversationId) {
        Objects.requireNonNull(foundationConversationId, "foundationConversationId");
        return new BuggySessionContext(
                UUID.randomUUID(),
                runtimeInstance.runtimeInstanceId(),
                runtimeInstance.assistantId(),
                foundationConversationId,
                runtimeInstance.configurationRevision(),
                clock.instant());
    }
}
