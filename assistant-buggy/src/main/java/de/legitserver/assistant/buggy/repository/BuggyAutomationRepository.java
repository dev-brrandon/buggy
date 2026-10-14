package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRun;
import de.legitserver.assistant.buggy.automation.BuggySignalType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BuggyAutomationRepository {
    Optional<BuggyAutomationRevision> findCurrent(String automationId);
    List<BuggyAutomationRevision> history(String automationId);
    default List<BuggyAutomationRevision> listCurrentAutomations(int limit) { return List.of(); }
    BuggyAutomationRevision append(long expectedCurrentRevision, BuggyAutomationRevision revision);
    List<BuggyAutomationRevision> enabledForSignal(BuggySignalType signalType);
    List<BuggyAutomationRevision> dueScheduled(Instant at);
    void appendRun(BuggyAutomationRun run);
    Optional<BuggyAutomationRun> latestRun(String automationId);
}
