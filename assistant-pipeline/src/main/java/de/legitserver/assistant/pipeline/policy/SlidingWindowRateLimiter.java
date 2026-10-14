package de.legitserver.assistant.pipeline.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe bounded sliding-window limiter keyed by actor + assistant + capability. */
public final class SlidingWindowRateLimiter {
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final int maxBuckets;
    private final ConcurrentHashMap<RateLimitKey, Bucket> buckets = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxRequests, Duration window, int maxBuckets, Clock clock) {
        if (maxRequests < 1) throw new IllegalArgumentException("maxRequests must be >= 1");
        if (window == null || window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be positive");
        if (maxBuckets < 1) throw new IllegalArgumentException("maxBuckets must be >= 1");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxBuckets = maxBuckets;
        this.clock = clock;
    }

    public boolean tryAcquire(RateLimitKey key) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxBuckets) return false;
            bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        synchronized (bucket) {
            while (!bucket.timestamps.isEmpty() && !bucket.timestamps.peekFirst().isAfter(cutoff)) {
                bucket.timestamps.removeFirst();
            }
            if (bucket.timestamps.size() >= maxRequests) return false;
            bucket.timestamps.addLast(now);
            return true;
        }
    }

    private static final class Bucket {
        private final ArrayDeque<Instant> timestamps = new ArrayDeque<>();
    }
}
