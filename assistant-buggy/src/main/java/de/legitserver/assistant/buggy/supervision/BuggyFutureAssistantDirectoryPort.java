package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;

/** Future adapter seam. It reports registration/runtime presence but never creates assistant identity. */
@FunctionalInterface
public interface BuggyFutureAssistantDirectoryPort {
    BuggyFutureAssistantPresence resolve(AssistantId targetAssistant);
}
