package de.legitserver.assistant.buggy.runtime;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** One process/runtime incarnation of Buggy. It is explicitly not the persistent Buggy identity. */
public record BuggyRuntimeInstance(
        UUID runtimeInstanceId,
        AssistantId assistantId,
        long identityHeadRevision,
        long configurationRevision,
        String runtimeProfileId,
        Instant startedAt) {

    public BuggyRuntimeInstance {
        runtimeInstanceId = CoreValidation.requireNonNull(runtimeInstanceId, "runtimeInstanceId");
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        if (assistantId != AssistantId.BUGGY) throw new IllegalArgumentException("Buggy runtime must use BUGGY identity");
        if (identityHeadRevision < 1 || configurationRevision < 1) {
            throw new IllegalArgumentException("identity/configuration revisions must be >= 1");
        }
        runtimeProfileId = CoreValidation.requireSafeToken(runtimeProfileId, "runtimeProfileId", 96);
        startedAt = CoreValidation.requireNonNull(startedAt, "startedAt");
    }
}
