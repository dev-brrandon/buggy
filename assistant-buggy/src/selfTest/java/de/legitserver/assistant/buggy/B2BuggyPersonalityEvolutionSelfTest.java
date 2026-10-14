package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.config.BuggyManifestCanonicalizer;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityDomain;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceRef;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceType;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationDraft;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevisionOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyPersonalityService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Offline B2 verification for seed, learning boundaries, append-only history and rollback. */
public final class B2BuggyPersonalityEvolutionSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T21:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:owner-canonical");
    private static final ActorId BUGGY_SYSTEM = new ActorId("system:buggy-personality");

    private B2BuggyPersonalityEvolutionSelfTest() {}

    public static void main(String[] args) {
        seedAndProfileActivation();
        ownerPreferenceLearningAndHistory();
        weakLearningDoesNotPromote();
        authorityMutationIsImpossibleThroughPersonality();
        rollbackAppendsInsteadOfRewriting();
        rollbackCanRemoveAttributesAddedAfterTargetRevision();
        secretLikeTextIsRejectedByRequiredContentBoundary();
        System.out.println("B2_BUGGY_PERSONALITY_EVOLUTION_SELF_TEST: PASS");
    }

    private static void seedAndProfileActivation() {
        Fixture fixture = fixture();
        BuggyPersonalitySnapshot state = fixture.personality.initialize(BUGGY_SYSTEM);
        require(state.current().revision() == 1, "initial personality revision");
        require(state.current().origin() == BuggyPersonalityRevisionOrigin.SEED, "initial revision origin");
        require(state.seed().profileId().equals(BuggyPersonalitySeed.PROFILE_ID), "canonical profile id");
        require(state.seed().entries().size() == 11, "canonical seed entries");
        BuggyIdentitySnapshot identity = fixture.identity.loadRequired();
        require(identity.configuration().manifest().personalityProfileId().equals(BuggyPersonalitySeed.PROFILE_ID),
                "personality profile activated by versioned bot configuration");
        require(!identity.configuration().manifest().selfApprovalAllowed(), "personality activation cannot enable self approval");
        require(!identity.configuration().manifest().authoritySelfEscalationAllowed(),
                "personality activation cannot enable authority escalation");
        require(!identity.configuration().manifest().riskDowngradeAllowed(),
                "personality activation cannot downgrade risk");
    }

    private static void ownerPreferenceLearningAndHistory() {
        Fixture fixture = fixture();
        fixture.personality.initialize(BUGGY_SYSTEM);
        BuggyPersonalityObservation observation = fixture.personality.recordObservation(new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.OWNER_PREFERENCE,
                "technical_disagreement",
                "prefer direct technical disagreement over diplomatic agreement",
                BuggyPersonalityObservationOrigin.EXPLICIT_OWNER,
                9_500,
                List.of(evidence(BuggyPersonalityEvidenceType.OWNER_STATEMENT_REF, "owner-statement-001")),
                "Owner explicitly chose direct technical disagreement",
                OWNER));
        BuggyPersonalitySnapshot evolved = fixture.personality.promoteObservations(
                List.of(observation.observationId()), BUGGY_SYSTEM, "Promote explicit Owner preference");
        require(evolved.current().revision() == 2, "personality revision advanced");
        require(evolved.current().changedAttributes().equals(List.of("OWNER_PREFERENCE:technical_disagreement")),
                "changed personality attribute recorded explicitly");
        require(evolved.current().evidenceObservationIds().equals(List.of(observation.observationId())),
                "revision evidence observation recorded explicitly");
        require(findValue(evolved.current(), BuggyPersonalityDomain.OWNER_PREFERENCE, "technical_disagreement")
                        .equals("prefer direct technical disagreement over diplomatic agreement"),
                "Owner preference promoted into state");
        var history = fixture.personality.historyView();
        require(history.revisions().size() == 2, "revision history retained");
        require(history.observations().size() == 1, "observation journal retained");
        require(history.observations().get(0).evidence().get(0).referenceId().equals("owner-statement-001"),
                "Personality stores evidence references rather than source bodies");
    }

    private static void weakLearningDoesNotPromote() {
        Fixture fixture = fixture();
        fixture.personality.initialize(BUGGY_SYSTEM);
        BuggyPersonalityObservation weak = fixture.personality.recordObservation(new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.OWNER_PREFERENCE,
                "response_concision",
                "prefer short active-incident replies",
                BuggyPersonalityObservationOrigin.REPEATED_OWNER_PATTERN,
                7_500,
                List.of(
                        evidence(BuggyPersonalityEvidenceType.CONVERSATION_REF, "conversation-001"),
                        evidence(BuggyPersonalityEvidenceType.CONVERSATION_REF, "conversation-002")),
                "Possible repeated preference, not enough evidence yet",
                BUGGY_SYSTEM));
        expectFailure(() -> fixture.personality.promoteObservations(
                List.of(weak.observationId()), BUGGY_SYSTEM, "Attempt weak inference promotion"));
        require(fixture.personality.loadRequired().current().revision() == 1,
                "weak observation remains journal-only and does not mutate personality state");
    }

    private static void authorityMutationIsImpossibleThroughPersonality() {
        expectFailure(() -> new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.OWNER_PREFERENCE,
                "permission_grant",
                "admin",
                BuggyPersonalityObservationOrigin.EXPLICIT_OWNER,
                10_000,
                List.of(evidence(BuggyPersonalityEvidenceType.OWNER_STATEMENT_REF, "owner-statement-002")),
                "invalid protected namespace",
                OWNER));
        expectFailure(() -> new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.INTERACTION_HABIT,
                "deployment_habit",
                "grant permission admin before deploy",
                BuggyPersonalityObservationOrigin.SELF_REVIEW,
                10_000,
                List.of(evidence(BuggyPersonalityEvidenceType.SELF_REVIEW_REF, "self-review-001")),
                "invalid authority encoding",
                BUGGY_SYSTEM));
    }

    private static void rollbackAppendsInsteadOfRewriting() {
        Fixture fixture = fixture();
        BuggyPersonalitySnapshot initial = fixture.personality.initialize(BUGGY_SYSTEM);
        String original = findValue(initial.current(), BuggyPersonalityDomain.COMMUNICATION_STYLE, "directness");
        BuggyPersonalityObservation observation = fixture.personality.recordObservation(new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.COMMUNICATION_STYLE,
                "directness",
                "very direct during active technical work",
                BuggyPersonalityObservationOrigin.EXPLICIT_OWNER,
                9_000,
                List.of(evidence(BuggyPersonalityEvidenceType.OWNER_STATEMENT_REF, "owner-statement-003")),
                "Owner requested stronger directness",
                OWNER));
        fixture.personality.promoteObservations(
                List.of(observation.observationId()), BUGGY_SYSTEM, "Apply stronger directness");
        BuggyPersonalitySnapshot rolledBack = fixture.personality.rollback(
                1L, OWNER, "Restore canonical seed state");
        require(rolledBack.current().revision() == 3, "rollback creates a new revision");
        require(rolledBack.current().rollbackTargetRevision() == 1L, "rollback target retained");
        require(rolledBack.current().evidenceObservationIds().isEmpty(), "rollback is evidenced by target revision, not fabricated observations");
        require(rolledBack.current().changedAttributes().contains("COMMUNICATION_STYLE:directness"),
                "rollback delta recorded explicitly");
        require(findValue(rolledBack.current(), BuggyPersonalityDomain.COMMUNICATION_STYLE, "directness").equals(original),
                "rollback restored earlier state");
        List<BuggyPersonalityRevision> history = fixture.personality.historyView().revisions();
        require(history.size() == 3, "rollback did not erase history");
        require(findValue(history.get(1), BuggyPersonalityDomain.COMMUNICATION_STYLE, "directness")
                        .equals("very direct during active technical work"),
                "superseded revision remains immutable and visible");
    }

    private static void rollbackCanRemoveAttributesAddedAfterTargetRevision() {
        Fixture fixture = fixture();
        fixture.personality.initialize(BUGGY_SYSTEM);
        BuggyPersonalityObservation observation = fixture.personality.recordObservation(new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.OWNER_PREFERENCE,
                "technical_disagreement",
                "prefer direct technical disagreement over diplomatic agreement",
                BuggyPersonalityObservationOrigin.EXPLICIT_OWNER,
                9_500,
                List.of(evidence(BuggyPersonalityEvidenceType.OWNER_STATEMENT_REF, "owner-statement-rollback-removal")),
                "Owner preference added after seed",
                OWNER));
        fixture.personality.promoteObservations(
                List.of(observation.observationId()), BUGGY_SYSTEM, "Promote removable Owner preference");

        BuggyPersonalitySnapshot rolledBack = fixture.personality.rollback(
                1L, OWNER, "Restore seed and remove post-seed attribute");
        require(rolledBack.current().changedAttributes().contains("OWNER_PREFERENCE:technical_disagreement"),
                "rollback records removed attribute in explicit delta");
        require(rolledBack.current().entries().stream()
                        .noneMatch(entry -> entry.identityKey().equals("OWNER_PREFERENCE:technical_disagreement")),
                "rollback may remove an attribute that did not exist in the target revision");
    }

    private static void secretLikeTextIsRejectedByRequiredContentBoundary() {
        Fixture fixture = fixture();
        fixture.personality.initialize(BUGGY_SYSTEM);
        expectFailure(() -> fixture.personality.recordObservation(new BuggyPersonalityObservationDraft(
                BuggyPersonalityDomain.INTERACTION_HABIT,
                "debug_notes",
                "password=super-secret-value",
                BuggyPersonalityObservationOrigin.SELF_REVIEW,
                9_000,
                List.of(evidence(BuggyPersonalityEvidenceType.SELF_REVIEW_REF, "self-review-002")),
                "must not persist",
                BUGGY_SYSTEM)));
        require(fixture.personality.historyView().observations().isEmpty(),
                "secret-like observation never reached persistence");
    }

    private static Fixture fixture() {
        InMemoryIdentityRepository identityRepository = new InMemoryIdentityRepository();
        BuggyIdentityService identity = new BuggyIdentityService(identityRepository);
        identity.initialize(BuggyBootstrapSeed.create(OWNER, BUGGY_SYSTEM, NOW));
        InMemoryPersonalityRepository personalityRepository = new InMemoryPersonalityRepository();
        Clock clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
        BuggyPersonalityService personality = new BuggyPersonalityService(
                identity,
                personalityRepository,
                (value, field, maxLength) -> {
                    String normalized = CoreValidation.requireText(value, field, maxLength);
                    if (normalized.toLowerCase(java.util.Locale.ROOT).contains("password=")) {
                        throw new IllegalArgumentException("test content boundary rejected secret-like text");
                    }
                    return normalized;
                },
                clock);
        return new Fixture(identity, personality);
    }

    private static BuggyPersonalityEvidenceRef evidence(
            BuggyPersonalityEvidenceType type, String referenceId) {
        return new BuggyPersonalityEvidenceRef(type, referenceId, NOW);
    }

    private static String findValue(
            BuggyPersonalityRevision revision, BuggyPersonalityDomain domain, String key) {
        return revision.entries().stream()
                .filter(entry -> entry.domain() == domain && entry.attributeKey().equals(key))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private record Fixture(BuggyIdentityService identity, BuggyPersonalityService personality) {}

    private static final class InMemoryIdentityRepository implements BuggyIdentityRepository {
        private BuggyIdentitySnapshot snapshot;

        @Override
        public Optional<BuggyIdentitySnapshot> find() {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
            if (snapshot != null) {
                if (!snapshot.identityCore().equals(seed.identityCore())
                        || !snapshot.ownerBinding().equals(seed.ownerBinding())) {
                    throw new IllegalStateException("different protected identity");
                }
                return snapshot;
            }
            snapshot = new BuggyIdentitySnapshot(
                    seed.identityCore(), seed.ownerBinding(), seed.configuration(), 1L, seed.configuration().createdAt());
            return snapshot;
        }

        @Override
        public BuggyIdentitySnapshot activatePersonalityProfile(
                String profileId, ActorId createdBy, Instant createdAt) {
            if (snapshot == null) throw new IllegalStateException("identity not initialized");
            String active = snapshot.configuration().manifest().personalityProfileId();
            if (profileId.equals(active)) return snapshot;
            if (active != null) throw new IllegalStateException("different personality profile active");
            BuggyManifest manifest = snapshot.configuration().manifest().withPersonalityProfileId(profileId);
            BuggyConfigurationRevision config = new BuggyConfigurationRevision(
                    snapshot.configuration().revision() + 1,
                    manifest,
                    createdBy,
                    createdAt,
                    BuggyManifestCanonicalizer.sha256(manifest));
            snapshot = new BuggyIdentitySnapshot(
                    snapshot.identityCore(), snapshot.ownerBinding(), config, snapshot.headRevision() + 1, createdAt);
            return snapshot;
        }
    }

    private static final class InMemoryPersonalityRepository implements BuggyPersonalityRepository {
        private BuggyPersonalitySeed seed;
        private final Map<Long, BuggyPersonalityRevision> revisions = new LinkedHashMap<>();
        private final Map<String, BuggyPersonalityObservation> observations = new LinkedHashMap<>();
        private long head;

        @Override
        public Optional<BuggyPersonalitySnapshot> find() {
            if (seed == null) return Optional.empty();
            BuggyPersonalityRevision current = revisions.get(head);
            return Optional.of(new BuggyPersonalitySnapshot(seed, current, current.createdAt()));
        }

        @Override
        public BuggyPersonalitySnapshot initialize(
                BuggyPersonalitySeed requestedSeed, BuggyPersonalityRevision initialRevision) {
            if (seed != null) {
                if (!seed.sha256().equals(requestedSeed.sha256())) {
                    throw new IllegalStateException("different seed");
                }
                return find().orElseThrow();
            }
            seed = requestedSeed;
            revisions.put(1L, initialRevision);
            head = 1L;
            return find().orElseThrow();
        }

        @Override
        public Optional<BuggyPersonalityRevision> findRevision(long revision) {
            return Optional.ofNullable(revisions.get(revision));
        }

        @Override
        public Optional<BuggyPersonalityObservation> findObservation(String observationId) {
            return Optional.ofNullable(observations.get(observationId));
        }

        @Override
        public List<BuggyPersonalityRevision> history() {
            return List.copyOf(revisions.values());
        }

        @Override
        public List<BuggyPersonalityObservation> observations() {
            return List.copyOf(observations.values());
        }

        @Override
        public BuggyPersonalityObservation appendObservation(BuggyPersonalityObservation observation) {
            BuggyPersonalityObservation previous = observations.putIfAbsent(observation.observationId(), observation);
            if (previous != null && !previous.equals(observation)) {
                throw new IllegalStateException("observation id collision");
            }
            return previous == null ? observation : previous;
        }

        @Override
        public BuggyPersonalitySnapshot appendRevision(
                long expectedCurrentRevision, BuggyPersonalityRevision revision) {
            if (head != expectedCurrentRevision) throw new IllegalStateException("stale personality revision");
            if (revisions.putIfAbsent(revision.revision(), revision) != null) {
                throw new IllegalStateException("revision already exists");
            }
            head = revision.revision();
            return find().orElseThrow();
        }
    }
}
