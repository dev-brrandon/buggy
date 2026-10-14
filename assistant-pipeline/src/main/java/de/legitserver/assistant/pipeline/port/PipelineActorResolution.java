package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.actor.ActorContext;

/** Fail-closed actor-resolution result used before authorization. */
public record PipelineActorResolution(boolean resolved, String safeCode, ActorContext actor) {
    public PipelineActorResolution {
        if (safeCode == null || safeCode.isBlank() || safeCode.length() > 96) {
            throw new IllegalArgumentException("safeCode must contain 1..96 characters");
        }
        if (resolved != (actor != null)) {
            throw new IllegalArgumentException("resolved state and actor must agree");
        }
    }

    public static PipelineActorResolution found(ActorContext actor) {
        return new PipelineActorResolution(true, "ACTOR_RESOLVED", actor);
    }

    public static PipelineActorResolution denied(String safeCode) {
        return new PipelineActorResolution(false, safeCode, null);
    }
}
