package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidate;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecisionType;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.memory.BuggyMemoryProvenance;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRetention;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryState;
import de.legitserver.assistant.buggy.memory.BuggyMemoryType;
import de.legitserver.assistant.buggy.repository.BuggyMemoryRepository;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * G1.5 Native Memory application service. It owns no NetworkCore authority and does not copy full
 * conversation/knowledge payloads: SESSION and KNOWLEDGE entries are compact memory assertions with source refs.
 */
public final class BuggyMemoryService {
    private final BuggyIdentityService identityService;
    private final BuggyMemoryRepository repository;
    private final BuggyMemoryContentPolicy contentPolicy;
    private final Clock clock;

    public BuggyMemoryService(
            BuggyIdentityService identityService,
            BuggyMemoryRepository repository,
            BuggyMemoryContentPolicy contentPolicy,
            Clock clock) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyMemoryCandidate propose(
            BuggyMemoryType type,
            ActorId subjectActorId,
            String key,
            String content,
            BuggyMemoryProvenance provenance,
            String sourceRef,
            int confidenceBps,
            BuggyMemoryRetention retention,
            boolean pinned,
            ActorId createdBy) {
        BuggyIdentitySnapshot identity = identityService.loadRequired();
        requireOwnerSubject(identity, type, subjectActorId);
        BuggyMemoryCandidate candidate = new BuggyMemoryCandidate(
                UUID.randomUUID(),
                type,
                subjectActorId,
                key,
                contentPolicy.requirePersistableText(content, "content", 16_384),
                provenance,
                contentPolicy.requirePersistableText(sourceRef, "sourceRef", 1024),
                confidenceBps,
                retention,
                pinned,
                createdBy,
                clock.instant());
        repository.appendCandidate(candidate);
        return candidate;
    }

    public BuggyMemoryRevision acceptCandidate(UUID candidateId, String reason, ActorId decidedBy) {
        BuggyMemoryCandidate candidate = requiredCandidate(candidateId);
        requireUndecided(candidateId);
        BuggyIdentitySnapshot identity = identityService.loadRequired();
        requireOwnerSubject(identity, candidate.type(), candidate.subjectActorId());
        BuggyMemoryId memoryId = BuggyMemoryId.random();
        var decision = new BuggyMemoryCandidateDecision(
                candidateId,
                BuggyMemoryCandidateDecisionType.ACCEPTED,
                memoryId,
                contentPolicy.requirePersistableText(reason, "reason", 1024),
                decidedBy,
                clock.instant());
        BuggyMemoryRevision revision = new BuggyMemoryRevision(
                memoryId,
                1L,
                null,
                candidate.type(),
                candidate.subjectActorId(),
                candidate.key(),
                candidate.content(),
                candidate.provenance(),
                candidate.sourceRef(),
                candidate.confidenceBps(),
                candidate.retention(),
                candidate.pinned(),
                BuggyMemoryState.ACTIVE,
                decidedBy,
                decision.decidedAt());
        return repository.acceptCandidate(decision, revision);
    }

    public void rejectCandidate(UUID candidateId, String reason, ActorId decidedBy) {
        requiredCandidate(candidateId);
        requireUndecided(candidateId);
        repository.rejectCandidate(new BuggyMemoryCandidateDecision(
                candidateId,
                BuggyMemoryCandidateDecisionType.REJECTED,
                null,
                contentPolicy.requirePersistableText(reason, "reason", 1024),
                decidedBy,
                clock.instant()));
    }

    /** Supersedes the current revision while keeping all older revisions immutable and queryable. */
    public BuggyMemoryRevision supersede(
            BuggyMemoryId memoryId,
            long expectedCurrentRevision,
            String content,
            BuggyMemoryProvenance provenance,
            String sourceRef,
            int confidenceBps,
            BuggyMemoryRetention retention,
            boolean pinned,
            ActorId changedBy) {
        BuggyMemoryRevision current = current(memoryId, expectedCurrentRevision);
        requireActive(current);
        return repository.appendRevision(expectedCurrentRevision, new BuggyMemoryRevision(
                memoryId,
                expectedCurrentRevision + 1,
                expectedCurrentRevision,
                current.type(),
                current.subjectActorId(),
                current.key(),
                contentPolicy.requirePersistableText(content, "content", 16_384),
                provenance,
                contentPolicy.requirePersistableText(sourceRef, "sourceRef", 1024),
                confidenceBps,
                retention,
                pinned,
                BuggyMemoryState.ACTIVE,
                changedBy,
                clock.instant()));
    }

    public BuggyMemoryRevision changeRetention(
            BuggyMemoryId memoryId,
            long expectedCurrentRevision,
            BuggyMemoryRetention retention,
            boolean pinned,
            ActorId changedBy) {
        BuggyMemoryRevision current = current(memoryId, expectedCurrentRevision);
        requireActive(current);
        return repository.appendRevision(expectedCurrentRevision, new BuggyMemoryRevision(
                memoryId,
                expectedCurrentRevision + 1,
                expectedCurrentRevision,
                current.type(),
                current.subjectActorId(),
                current.key(),
                current.content(),
                current.provenance(),
                current.sourceRef(),
                current.confidenceBps(),
                retention,
                pinned,
                BuggyMemoryState.ACTIVE,
                changedBy,
                clock.instant()));
    }

    public BuggyMemoryRevision retire(BuggyMemoryId memoryId, long expectedCurrentRevision, ActorId changedBy) {
        BuggyMemoryRevision current = current(memoryId, expectedCurrentRevision);
        requireActive(current);
        return repository.appendRevision(expectedCurrentRevision, new BuggyMemoryRevision(
                memoryId,
                expectedCurrentRevision + 1,
                expectedCurrentRevision,
                current.type(),
                current.subjectActorId(),
                current.key(),
                current.content(),
                current.provenance(),
                current.sourceRef(),
                current.confidenceBps(),
                current.retention(),
                current.pinned(),
                BuggyMemoryState.RETIRED,
                changedBy,
                clock.instant()));
    }

    /** Trusted internal retrieval for Buggy's own reasoning layer. No transport should expose this method directly. */
    public Optional<BuggyMemoryRevision> findInternal(BuggyMemoryId memoryId) {
        return repository.findCurrent(memoryId);
    }

    /** Actor-projected retrieval prevents Owner Memory from leaking merely because a caller can reach Buggy. */
    public Optional<BuggyMemoryRevision> findForActor(BuggyMemoryId memoryId, ActorId actorId) {
        Optional<BuggyMemoryRevision> result = repository.findCurrent(memoryId);
        result.ifPresent(memory -> requireReadableBy(memory, actorId));
        return result;
    }

    public List<BuggyMemoryRevision> listForActor(BuggyMemoryType type, int limit, ActorId actorId) {
        requireLimit(limit);
        return repository.listCurrent(type, limit).stream()
                .filter(memory -> canRead(memory, actorId))
                .toList();
    }

    public List<BuggyMemoryRevision> historyForActor(BuggyMemoryId memoryId, ActorId actorId) {
        List<BuggyMemoryRevision> history = repository.history(memoryId);
        if (!history.isEmpty()) requireReadableBy(history.getLast(), actorId);
        return history;
    }

    private BuggyMemoryCandidate requiredCandidate(UUID candidateId) {
        return repository.findCandidate(Objects.requireNonNull(candidateId, "candidateId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown memory candidate"));
    }

    private void requireUndecided(UUID candidateId) {
        if (repository.findCandidateDecision(candidateId).isPresent()) {
            throw new IllegalStateException("Memory candidate already decided");
        }
    }

    private BuggyMemoryRevision current(BuggyMemoryId memoryId, long expectedCurrentRevision) {
        BuggyMemoryRevision current = repository.findCurrent(Objects.requireNonNull(memoryId, "memoryId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown memory"));
        if (current.revision() != expectedCurrentRevision) {
            throw new IllegalStateException("MEMORY_REVISION_CONFLICT");
        }
        return current;
    }

    private static void requireActive(BuggyMemoryRevision current) {
        if (current.state() != BuggyMemoryState.ACTIVE) throw new IllegalStateException("Memory is not active");
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
    }

    private static void requireOwnerSubject(
            BuggyIdentitySnapshot identity, BuggyMemoryType type, ActorId subjectActorId) {
        if (type == BuggyMemoryType.PRIMARY_OWNER
                && !identity.ownerBinding().ownerActorId().equals(subjectActorId)) {
            throw new IllegalArgumentException("PRIMARY_OWNER memory must target the persistent Primary Owner");
        }
    }

    private void requireReadableBy(BuggyMemoryRevision memory, ActorId actorId) {
        if (!canRead(memory, actorId)) throw new SecurityException("BUGGY_MEMORY_NOT_VISIBLE_TO_ACTOR");
    }

    private boolean canRead(BuggyMemoryRevision memory, ActorId actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (memory.type() == BuggyMemoryType.PRIMARY_OWNER) {
            return identityService.loadRequired().ownerBinding().ownerActorId().equals(actorId);
        }
        if (memory.type() == BuggyMemoryType.RELATIONSHIP) {
            ActorId owner = identityService.loadRequired().ownerBinding().ownerActorId();
            return owner.equals(actorId) || memory.subjectActorId().equals(actorId);
        }
        return true;
    }
}
