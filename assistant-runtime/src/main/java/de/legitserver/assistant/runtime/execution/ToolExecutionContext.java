package de.legitserver.assistant.runtime.execution;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.Map;
import java.util.Objects;

/** Bounded data exposed to a registered handler after all pre-execution policy gates pass. */
public record ToolExecutionContext(
        ActorContext actor,
        ResourceRef resource,
        Map<String, Object> input) {
    public ToolExecutionContext {
        actor = Objects.requireNonNull(actor, "actor");
        resource = Objects.requireNonNull(resource, "resource");
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
