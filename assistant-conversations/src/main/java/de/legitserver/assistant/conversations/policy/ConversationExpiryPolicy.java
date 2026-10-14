package de.legitserver.assistant.conversations.policy;

import de.legitserver.assistant.conversations.model.ConversationContextType;
import java.time.Duration;
import java.time.Instant;

/** Context-sensitive inactivity expiry policy. Values are explicit Foundation defaults and can later be policy-driven. */
public final class ConversationExpiryPolicy {
    public Duration inactivityTimeout(ConversationContextType contextType) {
        if (contextType == null) throw new IllegalArgumentException("contextType must not be null");
        return switch (contextType) {
            case GENERAL -> Duration.ofHours(24);
            case BUG_INTAKE -> Duration.ofHours(72);
            case BUG_ANALYSIS -> Duration.ofHours(24);
            case KNOWLEDGE -> Duration.ofHours(4);
            case DIAGNOSTICS -> Duration.ofHours(1);
            case REQUEST -> Duration.ofHours(24);
        };
    }

    public Instant expiresAt(ConversationContextType contextType, Instant lastActivityAt) {
        if (lastActivityAt == null) throw new IllegalArgumentException("lastActivityAt must not be null");
        return lastActivityAt.plus(inactivityTimeout(contextType));
    }
}
