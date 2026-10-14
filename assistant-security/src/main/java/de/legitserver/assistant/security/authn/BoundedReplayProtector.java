package de.legitserver.assistant.security.authn;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory replay cache for short-lived signed service tokens. */
public final class BoundedReplayProtector implements ReplayProtector {
    private final int maxEntries;
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    public BoundedReplayProtector(int maxEntries) {
        if (maxEntries < 16) {
            throw new IllegalArgumentException("maxEntries must be >= 16");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized boolean claim(String credentialId, Instant expiresAt, Instant now) {
        String id = CoreValidation.requireSafeToken(credentialId, "credentialId", 128);
        CoreValidation.requireNonNull(expiresAt, "expiresAt");
        CoreValidation.requireNonNull(now, "now");
        seen.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (seen.containsKey(id)) {
            return false;
        }
        if (seen.size() >= maxEntries) {
            seen.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                    .ifPresent(entry -> seen.remove(entry.getKey(), entry.getValue()));
        }
        seen.put(id, expiresAt);
        return true;
    }

    public int size() {
        return seen.size();
    }
}
