package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.supervisor.health.AssistantHealthReport;
import java.util.Objects;

/** Combined future-presence + optional Foundation Supervisor health projection. */
public record BuggyFutureAssistantReadiness(
        AssistantId targetAssistant,
        BuggyFutureAssistantPresence presence,
        AssistantHealthReport health,
        boolean identityImplementedByB11,
        boolean personalityImplementedByB11,
        boolean authorityImplementedByB11) {
    public BuggyFutureAssistantReadiness {
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        presence = Objects.requireNonNull(presence, "presence");
        if (presence.assistantId() != targetAssistant) throw new IllegalArgumentException("presence target mismatch");
        if (!presence.supervisorReadable() && health != null) throw new IllegalArgumentException("unavailable target cannot have health");
        if (health != null && health.assistantId() != targetAssistant) throw new IllegalArgumentException("health target mismatch");
        if (identityImplementedByB11 || personalityImplementedByB11 || authorityImplementedByB11) {
            throw new IllegalArgumentException("B11 must not implement future assistant identity/personality/authority");
        }
    }
}
