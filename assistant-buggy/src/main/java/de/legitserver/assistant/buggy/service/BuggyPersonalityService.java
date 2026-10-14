package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityCanonicalizer;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityContentPolicy;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityHistoryView;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityLearningPolicy;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationDraft;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevisionOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityStateEntry;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * B2 product service for audited Personality evolution.
 * It may change style/preferences only; Foundation authority remains entirely outside this service.
 */
public final class BuggyPersonalityService {
    private final BuggyIdentityService identityService;
    private final BuggyPersonalityRepository repository;
    private final BuggyPersonalityContentPolicy contentPolicy;
    private final BuggyPersonalityLearningPolicy learningPolicy;
    private final Clock clock;

    public BuggyPersonalityService(
            BuggyIdentityService identityService,
            BuggyPersonalityRepository repository,
            BuggyPersonalityContentPolicy contentPolicy,
            Clock clock) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
        this.learningPolicy = new BuggyPersonalityLearningPolicy();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyPersonalitySnapshot loadRequired() {
        verifyIdentityProfile();
        return repository.find().orElseThrow(BuggyPersonalityUnavailableException::new);
    }

    public BuggyPersonalitySnapshot initialize(ActorId createdBy) {
        Objects.requireNonNull(createdBy, "createdBy");
        BuggyIdentitySnapshot identity = identityService.loadRequired();
        if (identity.configuration().manifest().personalityProfileId() == null) {
            identity = identityService.activatePersonalityProfile(
                    BuggyPersonalitySeed.PROFILE_ID, createdBy, clock.instant());
        }
        if (!BuggyPersonalitySeed.PROFILE_ID.equals(identity.configuration().manifest().personalityProfileId())) {
            throw new IllegalStateException("Buggy identity references an unsupported personality profile");
        }
        BuggyPersonalitySeed seed = BuggyPersonalitySeed.canonical();
        BuggyPersonalityRevision initial = BuggyPersonalityRevision.initial(seed, createdBy, clock.instant());
        return repository.initialize(seed, initial);
    }

    public BuggyPersonalityObservation recordObservation(BuggyPersonalityObservationDraft draft) {
        Objects.requireNonNull(draft, "draft");
        loadRequired();
        String value = contentPolicy.requirePersistableText(draft.observedValue(), "observedValue", 512);
        String note = contentPolicy.requirePersistableText(draft.note(), "note", 512);
        String observationId = "obs-" + UUID.randomUUID();
        Instant now = clock.instant();
        String sha = BuggyPersonalityCanonicalizer.observationSha256(
                observationId,
                draft.domain(),
                draft.attributeKey(),
                value,
                draft.origin(),
                draft.confidenceBps(),
                draft.evidence(),
                note,
                draft.createdBy(),
                now);
        BuggyPersonalityObservation observation = new BuggyPersonalityObservation(
                observationId,
                draft.domain(),
                draft.attributeKey(),
                value,
                draft.origin(),
                draft.confidenceBps(),
                draft.evidence(),
                note,
                draft.createdBy(),
                now,
                sha);
        learningPolicy.validateObservation(observation);
        return repository.appendObservation(observation);
    }

    public BuggyPersonalitySnapshot promoteObservations(
            List<String> observationIds, ActorId createdBy, String reason) {
        Objects.requireNonNull(observationIds, "observationIds");
        Objects.requireNonNull(createdBy, "createdBy");
        if (observationIds.isEmpty()) throw new IllegalArgumentException("At least one observation is required");
        String safeReason = contentPolicy.requirePersistableText(reason, "reason", 512);
        BuggyPersonalitySnapshot current = loadRequired();
        Map<String, BuggyPersonalityStateEntry> next = indexed(current.current().entries());
        int revisionConfidence = 10_000;
        Map<String, Boolean> changedKeys = new LinkedHashMap<>();

        for (String id : observationIds) {
            BuggyPersonalityObservation observation = repository.findObservation(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown personality observation: " + id));
            learningPolicy.requirePromotable(observation);
            BuggyPersonalityStateEntry entry = observation.toStateEntry();
            if (changedKeys.put(entry.identityKey(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Multiple promoted observations target the same personality attribute");
            }
            next.put(entry.identityKey(), entry);
            revisionConfidence = Math.min(revisionConfidence, observation.confidenceBps());
        }

        long revision = current.current().revision() + 1;
        List<BuggyPersonalityStateEntry> entries = sortedEntries(next);
        List<String> changedAttributes = changedKeys.keySet().stream().sorted().toList();
        List<String> evidenceObservationIds = observationIds.stream().distinct().sorted().toList();
        if (evidenceObservationIds.size() != observationIds.size()) {
            throw new IllegalArgumentException("Duplicate observation IDs are not allowed in one Personality revision");
        }
        Instant now = clock.instant();
        String sha = BuggyPersonalityCanonicalizer.revisionSha256(
                revision,
                current.current().revision(),
                BuggyPersonalityRevisionOrigin.OBSERVATION_PROMOTION,
                safeReason,
                revisionConfidence,
                null,
                changedAttributes,
                evidenceObservationIds,
                entries,
                createdBy,
                now);
        BuggyPersonalityRevision nextRevision = new BuggyPersonalityRevision(
                revision,
                current.current().revision(),
                BuggyPersonalityRevisionOrigin.OBSERVATION_PROMOTION,
                safeReason,
                revisionConfidence,
                null,
                changedAttributes,
                evidenceObservationIds,
                entries,
                createdBy,
                now,
                sha);
        return repository.appendRevision(current.current().revision(), nextRevision);
    }

    public BuggyPersonalitySnapshot rollback(long targetRevision, ActorId createdBy, String reason) {
        Objects.requireNonNull(createdBy, "createdBy");
        String safeReason = contentPolicy.requirePersistableText(reason, "reason", 512);
        BuggyPersonalitySnapshot current = loadRequired();
        if (targetRevision < 1 || targetRevision >= current.current().revision()) {
            throw new IllegalArgumentException("Rollback target must be an earlier personality revision");
        }
        BuggyPersonalityRevision target = repository.findRevision(targetRevision)
                .orElseThrow(() -> new IllegalArgumentException("Unknown personality revision: " + targetRevision));
        long revision = current.current().revision() + 1;
        List<BuggyPersonalityStateEntry> entries = sortedEntries(indexed(target.entries()));
        List<String> changedAttributes = changedAttributes(current.current().entries(), entries);
        if (changedAttributes.isEmpty()) {
            throw new IllegalArgumentException("Rollback target is state-equivalent to the current Personality revision");
        }
        List<String> evidenceObservationIds = List.of();
        Instant now = clock.instant();
        String sha = BuggyPersonalityCanonicalizer.revisionSha256(
                revision,
                current.current().revision(),
                BuggyPersonalityRevisionOrigin.ROLLBACK,
                safeReason,
                10_000,
                targetRevision,
                changedAttributes,
                evidenceObservationIds,
                entries,
                createdBy,
                now);
        BuggyPersonalityRevision rollback = new BuggyPersonalityRevision(
                revision,
                current.current().revision(),
                BuggyPersonalityRevisionOrigin.ROLLBACK,
                safeReason,
                10_000,
                targetRevision,
                changedAttributes,
                evidenceObservationIds,
                entries,
                createdBy,
                now,
                sha);
        return repository.appendRevision(current.current().revision(), rollback);
    }

    public BuggyPersonalityHistoryView historyView() {
        BuggyPersonalitySnapshot current = loadRequired();
        return new BuggyPersonalityHistoryView(
                current.seed(), current.current(), repository.history(), repository.observations());
    }

    private void verifyIdentityProfile() {
        String profile = identityService.loadRequired().configuration().manifest().personalityProfileId();
        if (!BuggyPersonalitySeed.PROFILE_ID.equals(profile)) {
            throw new BuggyPersonalityUnavailableException();
        }
    }

    private static Map<String, BuggyPersonalityStateEntry> indexed(List<BuggyPersonalityStateEntry> entries) {
        Map<String, BuggyPersonalityStateEntry> result = new LinkedHashMap<>();
        for (BuggyPersonalityStateEntry entry : entries) {
            if (result.put(entry.identityKey(), entry) != null) {
                throw new IllegalStateException("Duplicate personality entry in persisted state: " + entry.identityKey());
            }
        }
        return result;
    }

    private static List<String> changedAttributes(
            List<BuggyPersonalityStateEntry> current, List<BuggyPersonalityStateEntry> target) {
        Map<String, BuggyPersonalityStateEntry> currentByKey = indexed(current);
        Map<String, BuggyPersonalityStateEntry> targetByKey = indexed(target);
        java.util.Set<String> allKeys = new java.util.HashSet<>(currentByKey.keySet());
        allKeys.addAll(targetByKey.keySet());
        return allKeys.stream()
                .filter(key -> !Objects.equals(currentByKey.get(key), targetByKey.get(key)))
                .sorted()
                .toList();
    }

    private static List<BuggyPersonalityStateEntry> sortedEntries(Map<String, BuggyPersonalityStateEntry> entries) {
        List<BuggyPersonalityStateEntry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparing(BuggyPersonalityStateEntry::identityKey));
        return List.copyOf(result);
    }
}
