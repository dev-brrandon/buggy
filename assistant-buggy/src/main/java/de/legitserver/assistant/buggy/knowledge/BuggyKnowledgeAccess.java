package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Set;

/** Foundation-authorized caller projection used for B4 Knowledge access. */
public record BuggyKnowledgeAccess(
        ActorId actorId,
        boolean queryAuthorized,
        Set<String> effectivePermissions) {

    public BuggyKnowledgeAccess {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        effectivePermissions = effectivePermissions == null ? Set.of() : Set.copyOf(effectivePermissions);
    }
}
