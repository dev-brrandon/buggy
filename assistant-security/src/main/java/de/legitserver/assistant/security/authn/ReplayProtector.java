package de.legitserver.assistant.security.authn;

import java.time.Instant;

/** Tracks one-time credential identifiers; implementations must be bounded. */
public interface ReplayProtector {
    boolean claim(String credentialId, Instant expiresAt, Instant now);
}
