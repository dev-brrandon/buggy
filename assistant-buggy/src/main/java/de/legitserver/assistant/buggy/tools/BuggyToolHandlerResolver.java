package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.model.ToolId;
import java.util.Optional;

/** Resolves one explicitly implemented and boundary-declared handler per canonical Buggy tool. */
@FunctionalInterface
public interface BuggyToolHandlerResolver {
    Optional<BuggyToolHandlerBinding> resolve(ToolId toolId);
}
