package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidate;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for G1.5 memory candidates and append-only Native Memory revisions. */
public interface BuggyMemoryRepository {
    void appendCandidate(BuggyMemoryCandidate candidate);

    Optional<BuggyMemoryCandidate> findCandidate(UUID candidateId);

    Optional<BuggyMemoryCandidateDecision> findCandidateDecision(UUID candidateId);

    BuggyMemoryRevision acceptCandidate(BuggyMemoryCandidateDecision decision, BuggyMemoryRevision initialRevision);

    void rejectCandidate(BuggyMemoryCandidateDecision decision);

    Optional<BuggyMemoryRevision> findCurrent(BuggyMemoryId memoryId);

    List<BuggyMemoryRevision> history(BuggyMemoryId memoryId);

    List<BuggyMemoryRevision> listCurrent(BuggyMemoryType type, int limit);

    BuggyMemoryRevision appendRevision(long expectedCurrentRevision, BuggyMemoryRevision revision);
}
