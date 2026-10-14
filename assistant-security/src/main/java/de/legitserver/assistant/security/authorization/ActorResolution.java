package de.legitserver.assistant.security.authorization;

/** Fail-closed authoritative actor-resolution result. */
public record ActorResolution(AuthorityLookupStatus status, ResolvedActorIdentity identity) {
    public ActorResolution {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == AuthorityLookupStatus.FOUND && identity == null) {
            throw new IllegalArgumentException("FOUND requires identity");
        }
        if (status != AuthorityLookupStatus.FOUND && identity != null) {
            throw new IllegalArgumentException("non-FOUND result must not carry identity");
        }
    }

    public static ActorResolution found(ResolvedActorIdentity identity) {
        return new ActorResolution(AuthorityLookupStatus.FOUND, identity);
    }

    public static ActorResolution notFound() {
        return new ActorResolution(AuthorityLookupStatus.NOT_FOUND, null);
    }

    public static ActorResolution unavailable() {
        return new ActorResolution(AuthorityLookupStatus.UNAVAILABLE, null);
    }
}
