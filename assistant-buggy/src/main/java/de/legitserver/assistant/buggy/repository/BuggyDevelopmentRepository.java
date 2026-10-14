package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.development.BuggyLearningCandidate;
import de.legitserver.assistant.buggy.development.BuggyNativeDevelopmentJournalEntry;
import de.legitserver.assistant.buggy.development.BuggyReflectionRecord;
import de.legitserver.assistant.buggy.development.BuggySelfCapabilityRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for G1.5 Reflection, Learning Candidates, capability revisions and Development Journal. */
public interface BuggyDevelopmentRepository {
    BuggyReflectionRecord appendReflection(
            BuggyReflectionRecord reflection,
            BuggyNativeDevelopmentJournalEntry journalEntry);

    Optional<BuggyReflectionRecord> findReflection(UUID reflectionId);

    BuggyLearningCandidate appendLearningCandidate(
            BuggyLearningCandidate candidate,
            BuggyNativeDevelopmentJournalEntry journalEntry);

    Optional<BuggyLearningCandidate> findLearningCandidate(UUID candidateId);

    List<BuggyLearningCandidate> recentLearningCandidates(int limit);

    Optional<BuggySelfCapabilityRevision> findCurrentCapabilityRevision(String capabilityId);

    List<BuggySelfCapabilityRevision> capabilityHistory(String capabilityId);

    BuggySelfCapabilityRevision appendCapabilityRevision(
            long expectedCurrentRevision,
            BuggySelfCapabilityRevision revision,
            BuggyNativeDevelopmentJournalEntry journalEntry);

    List<BuggyNativeDevelopmentJournalEntry> journal(int limit);
}
