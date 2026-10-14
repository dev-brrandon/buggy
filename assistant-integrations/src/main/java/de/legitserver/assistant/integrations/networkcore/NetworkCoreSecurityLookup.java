package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;

/** Transport-neutral NetworkCore security lookup response using the Assistant-owned projection. */
public record NetworkCoreSecurityLookup(AuthorityLookupStatus status, AuthoritativeSecuritySnapshot snapshot) {
    public NetworkCoreSecurityLookup {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (status == AuthorityLookupStatus.FOUND && snapshot == null) {
            throw new IllegalArgumentException("FOUND requires snapshot");
        }
        if (status != AuthorityLookupStatus.FOUND && snapshot != null) {
            throw new IllegalArgumentException("non-FOUND result must not carry snapshot");
        }
    }
}
