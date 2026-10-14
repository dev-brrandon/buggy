package de.legitserver.assistant.knowledge.access;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import java.util.Set;
import java.util.stream.Collectors;

/** Security projection used by Knowledge resource-level ACL evaluation. */
public record KnowledgeAccessContext(
        ActorId actorId,
        boolean queryAuthorized,
        Set<String> effectivePermissions,
        Set<KnowledgeDomain> allowedDomains,
        DataClassification maximumClassification) {

    public KnowledgeAccessContext {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        effectivePermissions = effectivePermissions == null
                ? Set.of()
                : effectivePermissions.stream()
                        .map(value -> CoreValidation.requireSafeToken(value, "effectivePermission", 160))
                        .collect(Collectors.toUnmodifiableSet());
        allowedDomains = allowedDomains == null ? Set.of() : Set.copyOf(allowedDomains);
        maximumClassification = CoreValidation.requireNonNull(maximumClassification, "maximumClassification");
    }
}
