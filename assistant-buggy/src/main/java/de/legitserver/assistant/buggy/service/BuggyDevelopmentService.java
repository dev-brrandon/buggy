package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.development.BuggyDevelopmentJournalType;
import de.legitserver.assistant.buggy.development.BuggyLearningCandidate;
import de.legitserver.assistant.buggy.development.BuggyNativeDevelopmentJournalEntry;
import de.legitserver.assistant.buggy.development.BuggyReflectionRecord;
import de.legitserver.assistant.buggy.development.BuggySelfCapabilityRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityEntry;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.repository.BuggyDevelopmentRepository;
import de.legitserver.assistant.buggy.repository.BuggyMemoryRepository;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** G1.5 development layer: bounded reflection records, unapplied learning candidates and current-self revisions. */
public final class BuggyDevelopmentService {
    private final BuggyNativeCoreService nativeCore;
    private final BuggyMemoryRepository memory;
    private final BuggyDevelopmentRepository development;
    private final BuggyMemoryContentPolicy contentPolicy;
    private final Clock clock;

    public BuggyDevelopmentService(
            BuggyNativeCoreService nativeCore,
            BuggyMemoryRepository memory,
            BuggyDevelopmentRepository development,
            BuggyMemoryContentPolicy contentPolicy,
            Clock clock) {
        this.nativeCore = Objects.requireNonNull(nativeCore, "nativeCore");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.development = Objects.requireNonNull(development, "development");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyReflectionRecord reflect(
            List<BuggyMemoryId> memoryRefs,
            String summary,
            String lesson,
            int confidenceBps,
            ActorId createdBy) {
        Objects.requireNonNull(memoryRefs, "memoryRefs");
        for (BuggyMemoryId memoryId : memoryRefs) {
            if (memory.findCurrent(memoryId).isEmpty()) {
                throw new IllegalArgumentException("Reflection references unknown memory: " + memoryId);
            }
        }
        BuggyReflectionRecord reflection = new BuggyReflectionRecord(
                UUID.randomUUID(),
                memoryRefs,
                contentPolicy.requirePersistableText(summary, "summary", 4096),
                contentPolicy.requirePersistableText(lesson, "lesson", 4096),
                confidenceBps,
                createdBy,
                clock.instant());
        BuggyNativeDevelopmentJournalEntry journal = journal(
                BuggyDevelopmentJournalType.REFLECTION_RECORDED,
                "Reflection recorded: " + reflection.summary(),
                List.of("reflection:" + reflection.reflectionId()),
                createdBy,
                reflection.createdAt());
        return development.appendReflection(reflection, journal);
    }

    public BuggyLearningCandidate proposeLearning(
            UUID reflectionId,
            String ruleKey,
            String proposal,
            int confidenceBps,
            ActorId createdBy) {
        BuggyReflectionRecord reflection = development.findReflection(Objects.requireNonNull(reflectionId, "reflectionId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown reflection"));
        BuggyLearningCandidate candidate = new BuggyLearningCandidate(
                UUID.randomUUID(),
                reflection.reflectionId(),
                ruleKey,
                contentPolicy.requirePersistableText(proposal, "proposal", 4096),
                confidenceBps,
                createdBy,
                clock.instant());
        BuggyNativeDevelopmentJournalEntry journal = journal(
                BuggyDevelopmentJournalType.LEARNING_CANDIDATE_CREATED,
                "Learning candidate created for " + candidate.ruleKey(),
                List.of(
                        "learning-candidate:" + candidate.candidateId(),
                        "reflection:" + reflection.reflectionId()),
                createdBy,
                candidate.createdAt());
        return development.appendLearningCandidate(candidate, journal);
    }

    /**
     * Revises Buggy's description of an already-known capability. This never changes NetworkCore permissions,
     * maturity stage, tool authority, or the immutable G1.4 birth baseline.
     */
    public BuggySelfCapabilityRevision reviseCapability(
            String capabilityId,
            BuggySelfCapabilityStatus status,
            int confidenceBps,
            String limitation,
            String evidenceRef,
            ActorId changedBy) {
        BuggySelfCapabilityEntry baseline = nativeCore.loadRequired().capabilities().stream()
                .filter(entry -> entry.capabilityId().equals(capabilityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Capability is not part of the G1.4 self-model baseline"));
        var current = development.findCurrentCapabilityRevision(capabilityId);
        long expected = current.map(BuggySelfCapabilityRevision::revision).orElse(0L);
        BuggySelfCapabilityRevision revision = new BuggySelfCapabilityRevision(
                baseline.capabilityId(),
                expected + 1,
                expected == 0 ? null : expected,
                status,
                confidenceBps,
                limitation == null ? null : contentPolicy.requirePersistableText(limitation, "limitation", 320),
                contentPolicy.requirePersistableText(evidenceRef, "evidenceRef", 512),
                changedBy,
                clock.instant());
        BuggyNativeDevelopmentJournalEntry journal = journal(
                BuggyDevelopmentJournalType.SELF_CAPABILITY_REVISED,
                "Self capability revised: " + capabilityId + " -> " + status,
                List.of(
                        "capability:" + capabilityId + ":revision:" + revision.revision(),
                        revision.evidenceRef()),
                changedBy,
                revision.changedAt());
        return development.appendCapabilityRevision(expected, revision, journal);
    }

    public BuggySelfCapabilityEntry currentCapability(String capabilityId) {
        BuggySelfCapabilityEntry baseline = nativeCore.loadRequired().capabilities().stream()
                .filter(entry -> entry.capabilityId().equals(capabilityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown capability"));
        return development.findCurrentCapabilityRevision(capabilityId)
                .map(revision -> new BuggySelfCapabilityEntry(
                        revision.capabilityId(), revision.status(), revision.confidenceBps(), revision.limitation()))
                .orElse(baseline);
    }

    public List<BuggyLearningCandidate> recentLearningCandidates(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return development.recentLearningCandidates(limit);
    }

    public List<BuggyNativeDevelopmentJournalEntry> journal(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return development.journal(limit);
    }

    private static BuggyNativeDevelopmentJournalEntry journal(
            BuggyDevelopmentJournalType type,
            String summary,
            List<String> refs,
            ActorId actor,
            java.time.Instant at) {
        return new BuggyNativeDevelopmentJournalEntry(UUID.randomUUID(), type, summary, refs, actor, at);
    }
}
