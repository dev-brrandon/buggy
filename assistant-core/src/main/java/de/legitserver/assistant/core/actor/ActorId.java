package de.legitserver.assistant.core.actor;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable NetworkCore-backed actor identifier as understood by the Assistant Platform. */
public record ActorId(String value) implements Comparable<ActorId> {

    public ActorId {
        value = CoreValidation.requireSafeToken(value, "actorId", 128);
    }

    @Override
    public int compareTo(ActorId other) {
        return value.compareTo(CoreValidation.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
