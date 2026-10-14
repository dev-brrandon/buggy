package de.legitserver.assistant.buggy.runtime;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** Ephemeral session binding to a Foundation conversation. Session lifetime never defines identity lifetime. */
public record BuggySessionContext(
        UUID sessionInstanceId,
        UUID runtimeInstanceId,
        AssistantId assistantId,
        UUID conversationId,
        long configurationRevision,
        Instant startedAt) {

    public BuggySessionContext {
        sessionInstanceId = CoreValidation.requireNonNull(sessionInstanceId, "sessionInstanceId");
        runtimeInstanceId = CoreValidation.requireNonNull(runtimeInstanceId, "runtimeInstanceId");
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        conversationId = CoreValidation.requireNonNull(conversationId, "conversationId");
        if (assistantId != AssistantId.BUGGY) throw new IllegalArgumentException("Buggy session must use BUGGY identity");
        if (configurationRevision < 1) throw new IllegalArgumentException("configurationRevision must be >= 1");
        startedAt = CoreValidation.requireNonNull(startedAt, "startedAt");
    }
}
