package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.bugs.validation.BugValidation;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Append-only history event. before/after payloads contain bounded safe domain fields only. */
public record BugHistoryEvent(
        UUID historyId,
        InternalBugId internalBugId,
        BugHistoryEventType eventType,
        ActorId actorId,
        Map<String, String> before,
        Map<String, String> after,
        String reason,
        Instant createdAt) {

    public BugHistoryEvent {
        Objects.requireNonNull(historyId, "historyId");
        Objects.requireNonNull(internalBugId, "internalBugId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(actorId, "actorId");
        before = BugValidation.historyMap(before);
        after = BugValidation.historyMap(after);
        reason = BugValidation.reason(reason);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static BugHistoryEvent create(
            InternalBugId bugId,
            BugHistoryEventType type,
            ActorId actorId,
            Map<String, String> before,
            Map<String, String> after,
            String reason,
            Instant createdAt) {
        return new BugHistoryEvent(UUID.randomUUID(), bugId, type, actorId, before, after, reason, createdAt);
    }
}
