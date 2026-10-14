package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.time.Clock;
import java.util.Objects;

/** Safe B11 default while BYTE and KAESE have not been implemented. */
public final class BuggyNotStartedFutureAssistantDirectory implements BuggyFutureAssistantDirectoryPort {
    private final Clock clock;

    public BuggyNotStartedFutureAssistantDirectory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public BuggyFutureAssistantPresence resolve(AssistantId targetAssistant) {
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        return new BuggyFutureAssistantPresence(targetAssistant, BuggyFutureAssistantState.NOT_STARTED, 0, clock.instant());
    }
}
