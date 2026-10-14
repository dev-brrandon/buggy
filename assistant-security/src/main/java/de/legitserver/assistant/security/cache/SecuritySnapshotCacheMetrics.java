package de.legitserver.assistant.security.cache;

/** Immutable cache counters for metrics export in later phases. */
public record SecuritySnapshotCacheMetrics(
        long hits,
        long misses,
        long refreshes,
        long invalidations,
        long evictions,
        int size) {}
