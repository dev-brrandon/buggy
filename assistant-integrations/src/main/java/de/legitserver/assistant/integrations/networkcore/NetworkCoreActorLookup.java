package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;

/** Transport-neutral NetworkCore actor lookup response. */
public record NetworkCoreActorLookup(AuthorityLookupStatus status, ResolvedActorIdentity identity) {
    public NetworkCoreActorLookup {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (status == AuthorityLookupStatus.FOUND && identity == null) {
            throw new IllegalArgumentException("FOUND requires identity");
        }
        if (status != AuthorityLookupStatus.FOUND && identity != null) {
            throw new IllegalArgumentException("non-FOUND result must not carry identity");
        }
    }
}
