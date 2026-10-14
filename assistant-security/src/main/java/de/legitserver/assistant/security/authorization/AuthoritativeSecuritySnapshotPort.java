package de.legitserver.assistant.security.authorization;

import java.util.UUID;

/** Reads authoritative security state. minimumRevision is a freshness hint, never an allow signal. */
public interface AuthoritativeSecuritySnapshotPort {
    SecuritySnapshotLookup lookup(UUID actorUuid, long minimumRevision);
}
