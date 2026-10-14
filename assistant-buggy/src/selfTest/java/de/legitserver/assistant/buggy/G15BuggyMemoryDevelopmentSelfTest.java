package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifestCanonicalizer;
import de.legitserver.assistant.buggy.development.BuggyLearningCandidate;
import de.legitserver.assistant.buggy.development.BuggyNativeDevelopmentJournalEntry;
import de.legitserver.assistant.buggy.development.BuggyReflectionRecord;
import de.legitserver.assistant.buggy.development.BuggySelfCapabilityRevision;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidate;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.memory.BuggyMemoryProvenance;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRetention;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryState;
import de.legitserver.assistant.buggy.memory.BuggyMemoryType;
import de.legitserver.assistant.buggy.nativecore.BuggyNativeCoreState;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.repository.BuggyDevelopmentRepository;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.buggy.repository.BuggyMemoryRepository;
import de.legitserver.assistant.buggy.repository.BuggyNativeCoreRepository;
import de.legitserver.assistant.buggy.service.BuggyDevelopmentService;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyMemoryService;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreService;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** G1.5 executable contract for Native Memory & Development without external inference or write authority. */
public final class G15BuggyMemoryDevelopmentSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-06T15:30:00Z");
    private static final ActorId OWNER = new ActorId("actor:primary-owner");
    private static final ActorId TEAM = new ActorId("actor:team-member");
    private static final ActorId OTHER = new ActorId("actor:other");
    private static final ActorId BUGGY = new ActorId("assistant:buggy");

    private G15BuggyMemoryDevelopmentSelfTest() {}

    public static void main(String[] args) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BuggyIdentityService identity = identityService();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryDevelopmentRepository developmentRepository = new InMemoryDevelopmentRepository();
        BuggyNativeCoreService nativeCore = nativeCoreService();
        BuggyMemoryContentPolicy policy = new TestContentPolicy();
        BuggyMemoryService memory = new BuggyMemoryService(identity, memoryRepository, policy, clock);
        BuggyDevelopmentService development = new BuggyDevelopmentService(
                nativeCore, memoryRepository, developmentRepository, policy, clock);

        BuggyMemoryRevision session = accept(memory, memory.propose(
                BuggyMemoryType.SESSION,
                null,
                "session.current.goal",
                "Continue G1.5 implementation from the current conversation context.",
                BuggyMemoryProvenance.EXPLICIT,
                "conversation:123e4567-e89b-12d3-a456-426614174000",
                10_000,
                BuggyMemoryRetention.SESSION,
                false,
                OWNER));
        require(session.revision() == 1, "session memory revision 1");
        require(memoryRepository.history(session.memoryId()).size() == 1, "session history persisted");

        BuggyMemoryRevision knowledge = accept(memory, memory.propose(
                BuggyMemoryType.KNOWLEDGE,
                null,
                "knowledge.g15.roadmap",
                "G1.5 owns memory/development and must not pull G1.6 natural conversation forward.",
                BuggyMemoryProvenance.DERIVED,
                "knowledge:buggy-native-core-roadmap:revision:1",
                9_500,
                BuggyMemoryRetention.LONG_TERM,
                true,
                BUGGY));
        require(knowledge.pinned(), "knowledge memory can be pinned");

        BuggyMemoryRevision experience = accept(memory, memory.propose(
                BuggyMemoryType.EXPERIENCE,
                null,
                "experience.g15.boundary",
                "Existing conversation and knowledge stores should be referenced rather than duplicated.",
                BuggyMemoryProvenance.OBSERVED,
                "g15:design-review:1",
                9_000,
                BuggyMemoryRetention.LONG_TERM,
                false,
                BUGGY));

        BuggyMemoryRevision relationship = accept(memory, memory.propose(
                BuggyMemoryType.RELATIONSHIP,
                TEAM,
                "relationship.team-member.context",
                "This relationship memory is scoped to one actor and the Primary Owner.",
                BuggyMemoryProvenance.EXPLICIT,
                "relationship:actor:team-member",
                8_000,
                BuggyMemoryRetention.LONG_TERM,
                false,
                OWNER));
        require(memory.findForActor(relationship.memoryId(), TEAM).isPresent(), "relationship subject can read own memory");
        require(memory.findForActor(relationship.memoryId(), OWNER).isPresent(), "Primary Owner can read relationship memory");
        expectSecurityFailure(() -> memory.findForActor(relationship.memoryId(), OTHER));

        expectFailure(() -> memory.propose(
                BuggyMemoryType.PRIMARY_OWNER,
                TEAM,
                "owner.preference.invalid",
                "Must not bind to a delegate.",
                BuggyMemoryProvenance.EXPLICIT,
                "owner:preference:test",
                10_000,
                BuggyMemoryRetention.PERMANENT,
                true,
                TEAM));

        BuggyMemoryRevision ownerMemory = accept(memory, memory.propose(
                BuggyMemoryType.PRIMARY_OWNER,
                OWNER,
                "owner.preference.implementation-order",
                "Follow the canonical G1.4 -> G1.5 -> G1.6 block order.",
                BuggyMemoryProvenance.EXPLICIT,
                "owner:conversation:roadmap-confirmation",
                10_000,
                BuggyMemoryRetention.PERMANENT,
                true,
                OWNER));
        require(memory.findForActor(ownerMemory.memoryId(), OWNER).isPresent(), "Owner memory visible to Primary Owner");
        expectSecurityFailure(() -> memory.findForActor(ownerMemory.memoryId(), TEAM));
        require(memory.findInternal(ownerMemory.memoryId()).isPresent(), "Buggy internal retrieval retains Owner memory");
        expectFailure(() -> memory.changeRetention(
                ownerMemory.memoryId(),
                ownerMemory.revision(),
                BuggyMemoryRetention.SESSION,
                false,
                OWNER));

        BuggyMemoryRevision superseded = memory.supersede(
                experience.memoryId(),
                1L,
                "Existing conversation and knowledge stores are authoritative sources; Native Memory stores compact assertions and refs.",
                BuggyMemoryProvenance.DERIVED,
                "g15:design-review:2",
                9_800,
                BuggyMemoryRetention.PERMANENT,
                true,
                BUGGY);
        require(superseded.revision() == 2, "superseding appends revision 2");
        require(memoryRepository.history(experience.memoryId()).getFirst().content().equals(experience.content()),
                "old memory revision remains immutable");
        require(memoryRepository.history(experience.memoryId()).size() == 2, "superseding keeps full history");

        BuggyMemoryCandidate rejected = memory.propose(
                BuggyMemoryType.EXPERIENCE,
                null,
                "experience.reject-me",
                "Candidate should be rejected without becoming memory.",
                BuggyMemoryProvenance.INFERRED,
                "g15:test:rejected-candidate",
                2_000,
                BuggyMemoryRetention.SHORT_TERM,
                false,
                BUGGY);
        memory.rejectCandidate(rejected.candidateId(), "Insufficient confidence", BUGGY);
        expectFailure(() -> memory.acceptCandidate(rejected.candidateId(), "late accept", BUGGY));

        expectFailure(() -> memory.propose(
                BuggyMemoryType.SESSION,
                null,
                "session.invalid.pinned",
                "Pinned session retention is forbidden.",
                BuggyMemoryProvenance.EXPLICIT,
                "conversation:deadbeef",
                9_000,
                BuggyMemoryRetention.SESSION,
                true,
                OWNER));
        expectFailure(() -> memory.propose(
                BuggyMemoryType.EXPERIENCE,
                null,
                "experience.secret",
                "SECRET=must-not-persist",
                BuggyMemoryProvenance.EXPLICIT,
                "g15:test:secret",
                10_000,
                BuggyMemoryRetention.LONG_TERM,
                false,
                OWNER));

        BuggyReflectionRecord reflection = development.reflect(
                List.of(ownerMemory.memoryId(), superseded.memoryId(), knowledge.memoryId()),
                "The G1.5 architecture preserves prior stores and owner isolation.",
                "Prefer references and append-only revisions over competing sources of truth.",
                9_700,
                BUGGY);
        require(reflection.memoryRefs().size() == 3, "reflection is evidence-bound to memories");

        BuggyLearningCandidate learning = development.proposeLearning(
                reflection.reflectionId(),
                "learning.architecture.single-source-of-truth",
                "When a Foundation store already owns data, Native Memory should retain a bounded assertion plus provenance reference.",
                9_600,
                BUGGY);
        require(development.recentLearningCandidates(10).contains(learning), "learning candidate persisted");

        require(development.currentCapability("memory.long-term").status() == BuggySelfCapabilityStatus.UNAVAILABLE,
                "G1.4 birth baseline still reports original long-term memory state before revision");
        expectFailure(() -> development.reviseCapability(
                "memory.long-term",
                BuggySelfCapabilityStatus.AVAILABLE,
                8_000,
                "SECRET=must-not-persist",
                "g1.5:selftest:secret-limitation",
                BUGGY));
        BuggySelfCapabilityRevision capability = development.reviseCapability(
                "memory.long-term",
                BuggySelfCapabilityStatus.AVAILABLE,
                8_000,
                "G1.5 Native Memory is implemented; broader learning/maturity remains deferred.",
                "g1.5:selftest:memory-development",
                BUGGY);
        require(capability.revision() == 1, "first current-self revision is revision 1");
        require(development.currentCapability("memory.long-term").status() == BuggySelfCapabilityStatus.AVAILABLE,
                "current self-model sees G1.5 capability revision");
        require(nativeCore.loadRequired().capabilities().stream()
                        .filter(item -> item.capabilityId().equals("memory.long-term"))
                        .findFirst().orElseThrow().status() == BuggySelfCapabilityStatus.UNAVAILABLE,
                "immutable G1.4 birth capability remains unchanged");
        require(development.journal(20).size() == 3,
                "native journal records reflection, learning candidate and capability revision");

        BuggyMemoryRevision retired = memory.retire(session.memoryId(), 1L, BUGGY);
        require(retired.state() == BuggyMemoryState.RETIRED, "session memory can be retired append-only");
        require(memoryRepository.history(session.memoryId()).size() == 2, "retirement appends history");

        System.out.println("G1.5 BUGGY MEMORY & DEVELOPMENT: PASS");
    }

    private static BuggyMemoryRevision accept(BuggyMemoryService service, BuggyMemoryCandidate candidate) {
        return service.acceptCandidate(candidate.candidateId(), "Accepted by G1.5 contract test", BUGGY);
    }

    private static BuggyIdentityService identityService() {
        BuggyBootstrapSeed seed = BuggyBootstrapSeed.create(OWNER, new ActorId("system:bootstrap"), NOW);
        return new BuggyIdentityService(new FixedIdentityRepository(new BuggyIdentitySnapshot(
                seed.identityCore(), seed.ownerBinding(), seed.configuration(), 1L, NOW)));
    }

    private static BuggyNativeCoreService nativeCoreService() {
        InMemoryNativeCoreRepository repository = new InMemoryNativeCoreRepository();
        BuggyNativeCoreService service = new BuggyNativeCoreService(repository);
        service.initializeFirstActivation(NOW.minusSeconds(60));
        return service;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected failure");
    }

    private static void expectSecurityFailure(Runnable action) {
        try {
            action.run();
        } catch (SecurityException expected) {
            return;
        }
        throw new IllegalStateException("Expected SecurityException");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static final class TestContentPolicy implements BuggyMemoryContentPolicy {
        @Override
        public String requirePersistableText(String value, String field, int maxLength) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
            String normalized = value.strip();
            if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " too long");
            if (normalized.contains("SECRET=")) throw new IllegalArgumentException(field + " contains secret material");
            return normalized;
        }

        @Override
        public boolean containsSecretMaterial(String value) {
            return value != null && value.contains("SECRET=");
        }
    }

    private static final class FixedIdentityRepository implements BuggyIdentityRepository {
        private final BuggyIdentitySnapshot snapshot;

        private FixedIdentityRepository(BuggyIdentitySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<BuggyIdentitySnapshot> find() {
            return Optional.of(snapshot);
        }

        @Override
        public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
            if (!snapshot.identityCore().equals(seed.identityCore()) || !snapshot.ownerBinding().equals(seed.ownerBinding())) {
                throw new IllegalStateException("Identity differs");
            }
            return snapshot;
        }

        @Override
        public BuggyIdentitySnapshot activatePersonalityProfile(String profileId, ActorId createdBy, Instant createdAt) {
            if (Objects.equals(snapshot.configuration().manifest().personalityProfileId(), profileId)) return snapshot;
            var manifest = snapshot.configuration().manifest().withPersonalityProfileId(profileId);
            return new BuggyIdentitySnapshot(
                    snapshot.identityCore(),
                    snapshot.ownerBinding(),
                    new BuggyConfigurationRevision(
                            snapshot.configuration().revision() + 1,
                            manifest,
                            createdBy,
                            createdAt,
                            BuggyManifestCanonicalizer.sha256(manifest)),
                    snapshot.headRevision() + 1,
                    createdAt);
        }
    }

    private static final class InMemoryNativeCoreRepository implements BuggyNativeCoreRepository {
        private BuggyNativeCoreState state;

        @Override
        public Optional<BuggyNativeCoreState> find() {
            return Optional.ofNullable(state);
        }

        @Override
        public BuggyNativeCoreState initialize(BuggyNativeCoreState requested) {
            if (state == null) state = requested;
            else if (!state.sha256().equals(requested.sha256())) throw new IllegalStateException("Native Core immutable");
            return state;
        }
    }

    private static final class InMemoryMemoryRepository implements BuggyMemoryRepository {
        private final Map<UUID, BuggyMemoryCandidate> candidates = new LinkedHashMap<>();
        private final Map<UUID, BuggyMemoryCandidateDecision> decisions = new LinkedHashMap<>();
        private final Map<BuggyMemoryId, List<BuggyMemoryRevision>> histories = new LinkedHashMap<>();

        @Override
        public void appendCandidate(BuggyMemoryCandidate candidate) {
            if (candidates.putIfAbsent(candidate.candidateId(), candidate) != null) {
                throw new IllegalStateException("duplicate candidate");
            }
        }

        @Override
        public Optional<BuggyMemoryCandidate> findCandidate(UUID candidateId) {
            return Optional.ofNullable(candidates.get(candidateId));
        }

        @Override
        public Optional<BuggyMemoryCandidateDecision> findCandidateDecision(UUID candidateId) {
            return Optional.ofNullable(decisions.get(candidateId));
        }

        @Override
        public BuggyMemoryRevision acceptCandidate(
                BuggyMemoryCandidateDecision decision, BuggyMemoryRevision initialRevision) {
            if (!candidates.containsKey(decision.candidateId())) throw new IllegalStateException("candidate missing");
            if (decisions.putIfAbsent(decision.candidateId(), decision) != null) throw new IllegalStateException("decided");
            histories.put(initialRevision.memoryId(), new ArrayList<>(List.of(initialRevision)));
            return initialRevision;
        }

        @Override
        public void rejectCandidate(BuggyMemoryCandidateDecision decision) {
            if (!candidates.containsKey(decision.candidateId())) throw new IllegalStateException("candidate missing");
            if (decisions.putIfAbsent(decision.candidateId(), decision) != null) throw new IllegalStateException("decided");
        }

        @Override
        public Optional<BuggyMemoryRevision> findCurrent(BuggyMemoryId memoryId) {
            List<BuggyMemoryRevision> history = histories.get(memoryId);
            return history == null ? Optional.empty() : Optional.of(history.getLast());
        }

        @Override
        public List<BuggyMemoryRevision> history(BuggyMemoryId memoryId) {
            List<BuggyMemoryRevision> history = histories.get(memoryId);
            return history == null ? List.of() : List.copyOf(history);
        }

        @Override
        public List<BuggyMemoryRevision> listCurrent(BuggyMemoryType type, int limit) {
            return histories.values().stream()
                    .map(List::getLast)
                    .filter(memory -> memory.type() == type && memory.state() == BuggyMemoryState.ACTIVE)
                    .sorted(Comparator.comparing(BuggyMemoryRevision::changedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public BuggyMemoryRevision appendRevision(long expectedCurrentRevision, BuggyMemoryRevision revision) {
            List<BuggyMemoryRevision> history = histories.get(revision.memoryId());
            if (history == null || history.getLast().revision() != expectedCurrentRevision) {
                throw new IllegalStateException("stale memory");
            }
            history.add(revision);
            return revision;
        }
    }

    private static final class InMemoryDevelopmentRepository implements BuggyDevelopmentRepository {
        private final Map<UUID, BuggyReflectionRecord> reflections = new LinkedHashMap<>();
        private final Map<UUID, BuggyLearningCandidate> learning = new LinkedHashMap<>();
        private final Map<String, List<BuggySelfCapabilityRevision>> capabilityHistory = new LinkedHashMap<>();
        private final List<BuggyNativeDevelopmentJournalEntry> journal = new ArrayList<>();

        @Override
        public BuggyReflectionRecord appendReflection(
                BuggyReflectionRecord reflection, BuggyNativeDevelopmentJournalEntry journalEntry) {
            reflections.put(reflection.reflectionId(), reflection);
            journal.add(journalEntry);
            return reflection;
        }

        @Override
        public Optional<BuggyReflectionRecord> findReflection(UUID reflectionId) {
            return Optional.ofNullable(reflections.get(reflectionId));
        }

        @Override
        public BuggyLearningCandidate appendLearningCandidate(
                BuggyLearningCandidate candidate, BuggyNativeDevelopmentJournalEntry journalEntry) {
            learning.put(candidate.candidateId(), candidate);
            journal.add(journalEntry);
            return candidate;
        }

        @Override
        public Optional<BuggyLearningCandidate> findLearningCandidate(UUID candidateId) {
            return Optional.ofNullable(learning.get(candidateId));
        }

        @Override
        public List<BuggyLearningCandidate> recentLearningCandidates(int limit) {
            return learning.values().stream()
                    .sorted(Comparator.comparing(BuggyLearningCandidate::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<BuggySelfCapabilityRevision> findCurrentCapabilityRevision(String capabilityId) {
            List<BuggySelfCapabilityRevision> history = capabilityHistory.get(capabilityId);
            return history == null ? Optional.empty() : Optional.of(history.getLast());
        }

        @Override
        public List<BuggySelfCapabilityRevision> capabilityHistory(String capabilityId) {
            List<BuggySelfCapabilityRevision> history = capabilityHistory.get(capabilityId);
            return history == null ? List.of() : List.copyOf(history);
        }

        @Override
        public BuggySelfCapabilityRevision appendCapabilityRevision(
                long expectedCurrentRevision,
                BuggySelfCapabilityRevision revision,
                BuggyNativeDevelopmentJournalEntry journalEntry) {
            List<BuggySelfCapabilityRevision> history = capabilityHistory.computeIfAbsent(
                    revision.capabilityId(), ignored -> new ArrayList<>());
            long actual = history.isEmpty() ? 0L : history.getLast().revision();
            if (actual != expectedCurrentRevision) throw new IllegalStateException("stale capability");
            history.add(revision);
            journal.add(journalEntry);
            return revision;
        }

        @Override
        public List<BuggyNativeDevelopmentJournalEntry> journal(int limit) {
            return journal.stream()
                    .sorted(Comparator.comparing(BuggyNativeDevelopmentJournalEntry::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
