package de.legitserver.assistant.security.authorization;

/** Fail-closed security-snapshot lookup result. */
public record SecuritySnapshotLookup(AuthorityLookupStatus status, AuthoritativeSecuritySnapshot snapshot) {
    public SecuritySnapshotLookup {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == AuthorityLookupStatus.FOUND && snapshot == null) {
            throw new IllegalArgumentException("FOUND requires snapshot");
        }
        if (status != AuthorityLookupStatus.FOUND && snapshot != null) {
            throw new IllegalArgumentException("non-FOUND result must not carry snapshot");
        }
    }

    public static SecuritySnapshotLookup found(AuthoritativeSecuritySnapshot snapshot) {
        return new SecuritySnapshotLookup(AuthorityLookupStatus.FOUND, snapshot);
    }

    public static SecuritySnapshotLookup notFound() {
        return new SecuritySnapshotLookup(AuthorityLookupStatus.NOT_FOUND, null);
    }

    public static SecuritySnapshotLookup unavailable() {
        return new SecuritySnapshotLookup(AuthorityLookupStatus.UNAVAILABLE, null);
    }
}
