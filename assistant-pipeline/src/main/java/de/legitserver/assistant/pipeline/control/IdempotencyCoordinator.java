package de.legitserver.assistant.pipeline.control;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory idempotency coordinator. Persistent distributed storage can replace this behind Phase-14 wiring. */
public final class IdempotencyCoordinator {
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();

    public IdempotencyCoordinator(Duration ttl, int maxEntries, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public IdempotencyDecision claim(PipelineRequest request, ActorId actorId, String requestHash) {
        if (request.idempotencyKey() == null) return new IdempotencyDecision(IdempotencyStatus.OWNER, null);
        cleanupExpired();
        Key key = new Key(actorId, request.assistant().assistantId(), request.idempotencyKey());
        Instant expiresAt = clock.instant().plus(ttl);
        Entry created = new Entry(requestHash, expiresAt);
        Entry existing = entries.putIfAbsent(key, created);
        if (existing == null) {
            if (entries.size() > maxEntries) {
                entries.remove(key, created);
                return new IdempotencyDecision(IdempotencyStatus.IN_FLIGHT, null);
            }
            return new IdempotencyDecision(IdempotencyStatus.OWNER, null);
        }
        synchronized (existing) {
            if (!existing.requestHash.equals(requestHash)) return new IdempotencyDecision(IdempotencyStatus.CONFLICT, null);
            if (existing.response != null) return new IdempotencyDecision(IdempotencyStatus.REPLAY, existing.response);
            return new IdempotencyDecision(IdempotencyStatus.IN_FLIGHT, null);
        }
    }

    public void complete(PipelineRequest request, ActorId actorId, String requestHash, PipelineResponse response) {
        if (request.idempotencyKey() == null) return;
        Key key = new Key(actorId, request.assistant().assistantId(), request.idempotencyKey());
        Entry entry = entries.get(key);
        if (entry == null || !entry.requestHash.equals(requestHash)) return;
        synchronized (entry) {
            entry.response = Objects.requireNonNull(response, "response");
        }
    }

    public void abandon(PipelineRequest request, ActorId actorId, String requestHash) {
        if (request.idempotencyKey() == null) return;
        Key key = new Key(actorId, request.assistant().assistantId(), request.idempotencyKey());
        entries.computeIfPresent(key, (ignored, entry) -> entry.requestHash.equals(requestHash) && entry.response == null ? null : entry);
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private record Key(ActorId actorId, AssistantId assistantId, String idempotencyKey) {
        private Key {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(assistantId, "assistantId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    private static final class Entry {
        private final String requestHash;
        private final Instant expiresAt;
        private PipelineResponse response;

        private Entry(String requestHash, Instant expiresAt) {
            this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
