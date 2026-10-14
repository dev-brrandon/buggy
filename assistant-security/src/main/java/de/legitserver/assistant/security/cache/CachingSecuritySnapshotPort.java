package de.legitserver.assistant.security.cache;

import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, short-TTL, revision-aware cache around the authoritative security snapshot source. */
public final class CachingSecuritySnapshotPort implements AuthoritativeSecuritySnapshotPort {
    private final AuthoritativeSecuritySnapshotPort delegate;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong refreshes = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public CachingSecuritySnapshotPort(
            AuthoritativeSecuritySnapshotPort delegate, Duration ttl, int maxEntries, Clock clock) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("security cache ttl must be > 0 and <= 30 seconds");
        }
        if (maxEntries < 16) throw new IllegalArgumentException("maxEntries must be >= 16");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    @Override
    public SecuritySnapshotLookup lookup(UUID actorUuid, long minimumRevision) {
        if (actorUuid == null || minimumRevision < 0) {
            return SecuritySnapshotLookup.unavailable();
        }
        Instant now = clock.instant();
        Entry current = cache.get(actorUuid);
        if (current != null
                && current.expiresAt().isAfter(now)
                && current.lookup().status() == AuthorityLookupStatus.FOUND
                && current.lookup().snapshot().securityRevision() >= minimumRevision) {
            hits.incrementAndGet();
            return current.lookup();
        }
        misses.incrementAndGet();
        SecuritySnapshotLookup fresh = delegate.lookup(actorUuid, minimumRevision);
        refreshes.incrementAndGet();
        if (fresh.status() == AuthorityLookupStatus.FOUND) {
            put(actorUuid, fresh, now.plus(ttl));
        } else {
            cache.remove(actorUuid);
        }
        return fresh;
    }

    public void invalidate(UUID actorUuid) {
        if (actorUuid != null && cache.remove(actorUuid) != null) {
            invalidations.incrementAndGet();
        }
    }

    public void invalidateAll() {
        int count = cache.size();
        cache.clear();
        if (count > 0) invalidations.addAndGet(count);
    }

    public SecuritySnapshotCacheMetrics metrics() {
        return new SecuritySnapshotCacheMetrics(
                hits.get(), misses.get(), refreshes.get(), invalidations.get(), evictions.get(), cache.size());
    }

    private synchronized void put(UUID actorUuid, SecuritySnapshotLookup lookup, Instant expiresAt) {
        if (!cache.containsKey(actorUuid) && cache.size() >= maxEntries) {
            cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.comparing(Entry::expiresAt)))
                    .ifPresent(entry -> {
                        if (cache.remove(entry.getKey(), entry.getValue())) evictions.incrementAndGet();
                    });
        }
        cache.put(actorUuid, new Entry(lookup, expiresAt));
    }

    private record Entry(SecuritySnapshotLookup lookup, Instant expiresAt) {}
}
