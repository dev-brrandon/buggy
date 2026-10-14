package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRun;
import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.config.BuggyManifestCanonicalizer;
import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterReadService;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyAutomationRepository;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.repository.BuggyTaskRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelation;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityFeature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** B8 verifies bounded read projections only; no Control Center write authority exists here. */
public final class B8BuggyControlCenterBackendSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:20:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-owner");
    private B8BuggyControlCenterBackendSelfTest() {}

    public static void main(String[] args) {
        IdentityMemory identityRepo = new IdentityMemory();
        BuggyIdentityService identities = new BuggyIdentityService(identityRepo);
        identities.initialize(BuggyBootstrapSeed.create(OWNER, new ActorId("system:bootstrap"), NOW));
        identities.activatePersonalityProfile(BuggyPersonalitySeed.PROFILE_ID, OWNER, NOW.plusSeconds(1));

        MemoryState state = new MemoryState();
        BuggyPersonalitySeed seed = BuggyPersonalitySeed.canonical();
        BuggyPersonalityRevision initial = BuggyPersonalityRevision.initial(seed, OWNER, NOW.plusSeconds(2));
        state.personality = new BuggyPersonalitySnapshot(seed, initial, NOW.plusSeconds(2));
        BuggyTaskId taskId = BuggyTaskId.random();
        state.tasks.put(taskId, new BuggyTaskRevision(taskId, 1, null, "Review incident evidence",
                "Correlate deployment and logs.", BuggyTaskStatus.WAITING_APPROVAL, BuggyAttentionLevel.ACTION_REQUIRED,
                70, "Owner decision required", OWNER, NOW.plusSeconds(3)));
        state.activities.add(new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.TASK_WAITING,
                BuggyAttentionLevel.ACTION_REQUIRED, taskId, null, "Task requires Owner decision",
                Map.of("status", "WAITING_APPROVAL"), OWNER, NOW.plusSeconds(4)));

        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
        BuggyControlCenterReadService reads = new BuggyControlCenterReadService(
                identities, state, state, state, state, state, new ToolRegistry(), controls);

        var overview = reads.overview();
        require(Boolean.TRUE.equals(overview.attributes().get("healthy")), "overview health");
        require(Long.valueOf(1L).equals(overview.attributes().get("waitingTasks")), "waiting task projection");
        require("NETWORKCORE".equals(reads.identity().attributes().get("securityAuthority")), "identity authority visible");
        require(Boolean.TRUE.equals(reads.identity().attributes().get("protectedIdentityCore")), "identity core protected");
        require(Long.valueOf(1L).equals(reads.personality().attributes().get("currentRevision")), "personality revision visible");
        require(reads.tasks(10).size() == 1, "task projection");
        require(reads.activity(10).size() == 1, "activity projection");
        require(reads.tools().size() == 157, "B5+B7 tool matrix projection");
        require("NetworkCore".equals(reads.security().attributes().get("authority")), "NetworkCore authority statement");
        require(Boolean.FALSE.equals(reads.security().attributes().get("selfApprovalAllowed")), "self approval remains impossible");
        System.out.println("B8_BUGGY_CONTROL_CENTER_BACKEND_SELF_TEST: PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("B8 Control Center self-test failed: " + message);
    }

    private static final class IdentityMemory implements BuggyIdentityRepository {
        private BuggyIdentitySnapshot snapshot;
        @Override public Optional<BuggyIdentitySnapshot> find() { return Optional.ofNullable(snapshot); }
        @Override public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
            if (snapshot == null) snapshot = new BuggyIdentitySnapshot(seed.identityCore(), seed.ownerBinding(), seed.configuration(), 1, seed.configuration().createdAt());
            return snapshot;
        }
        @Override public BuggyIdentitySnapshot activatePersonalityProfile(String profileId, ActorId createdBy, Instant createdAt) {
            if (snapshot == null) throw new IllegalStateException("identity absent");
            BuggyManifest manifest = snapshot.configuration().manifest().withPersonalityProfileId(profileId);
            BuggyConfigurationRevision configuration = new BuggyConfigurationRevision(snapshot.configuration().revision() + 1,
                    manifest, createdBy, createdAt, BuggyManifestCanonicalizer.sha256(manifest));
            snapshot = new BuggyIdentitySnapshot(snapshot.identityCore(), snapshot.ownerBinding(), configuration,
                    snapshot.headRevision() + 1, createdAt);
            return snapshot;
        }
    }

    private static final class MemoryState implements BuggyPersonalityRepository, BuggyTaskRepository,
            BuggyActivityRepository, BuggyAutomationRepository, BuggySelfDevelopmentRepository {
        private BuggyPersonalitySnapshot personality;
        private final Map<BuggyTaskId, BuggyTaskRevision> tasks = new HashMap<>();
        private final List<BuggyActivityEntry> activities = new ArrayList<>();

        @Override public Optional<BuggyPersonalitySnapshot> find() { return Optional.ofNullable(personality); }
        @Override public BuggyPersonalitySnapshot initialize(BuggyPersonalitySeed seed, BuggyPersonalityRevision revision) { personality = new BuggyPersonalitySnapshot(seed, revision, revision.createdAt()); return personality; }
        @Override public Optional<BuggyPersonalityRevision> findRevision(long revision) { return personality != null && personality.current().revision() == revision ? Optional.of(personality.current()) : Optional.empty(); }
        @Override public Optional<BuggyPersonalityObservation> findObservation(String id) { return Optional.empty(); }
        @Override public List<BuggyPersonalityRevision> history() { return personality == null ? List.of() : List.of(personality.current()); }
        @Override public List<BuggyPersonalityObservation> observations() { return List.of(); }
        @Override public BuggyPersonalityObservation appendObservation(BuggyPersonalityObservation o) { throw new UnsupportedOperationException(); }
        @Override public BuggyPersonalitySnapshot appendRevision(long expected, BuggyPersonalityRevision revision) { throw new UnsupportedOperationException(); }

        @Override public Optional<BuggyTaskRevision> findCurrent(BuggyTaskId id) { return Optional.ofNullable(tasks.get(id)); }
        @Override public List<BuggyTaskRevision> history(BuggyTaskId id) { return findCurrent(id).stream().toList(); }
        @Override public List<BuggyTaskRevision> listCurrentTasks(int limit) { return tasks.values().stream().limit(limit).toList(); }
        @Override public BuggyTaskRevision append(long expected, BuggyTaskRevision revision) { tasks.put(revision.taskId(), revision); return revision; }
        @Override public void addRelation(BuggyTaskRelation relation) { }
        @Override public List<BuggyTaskRelation> relations(BuggyTaskId id) { return List.of(); }

        @Override public void append(BuggyActivityEntry entry) { activities.add(entry); }
        @Override public List<BuggyActivityEntry> recent(int limit) { return activities.stream().sorted(java.util.Comparator.comparing(BuggyActivityEntry::occurredAt).reversed()).limit(limit).toList(); }

        @Override public Optional<BuggyAutomationRevision> findCurrent(String id) { return Optional.empty(); }
        @Override public List<BuggyAutomationRevision> history(String id) { return List.of(); }
        @Override public List<BuggyAutomationRevision> listCurrentAutomations(int limit) { return List.of(); }
        @Override public BuggyAutomationRevision append(long expected, BuggyAutomationRevision revision) { throw new UnsupportedOperationException(); }
        @Override public List<BuggyAutomationRevision> enabledForSignal(de.legitserver.assistant.buggy.automation.BuggySignalType type) { return List.of(); }
        @Override public List<BuggyAutomationRevision> dueScheduled(Instant at) { return List.of(); }
        @Override public void appendRun(BuggyAutomationRun run) { }
        @Override public Optional<BuggyAutomationRun> latestRun(String id) { return Optional.empty(); }

        @Override public Optional<BuggySelfDevelopmentRevision> findCurrent(BuggySelfDevelopmentId id) { return Optional.empty(); }
        @Override public List<BuggySelfDevelopmentRevision> history(BuggySelfDevelopmentId id) { return List.of(); }
        @Override public List<BuggySelfDevelopmentRevision> listCurrentItems(int limit) { return List.of(); }
        @Override public BuggySelfDevelopmentRevision append(long expected, BuggySelfDevelopmentRevision revision) { throw new UnsupportedOperationException(); }
        @Override public void appendEvidence(BuggySelfDevelopmentEvidence evidence) { }
        @Override public List<BuggySelfDevelopmentEvidence> evidence(BuggySelfDevelopmentId id) { return List.of(); }
    }
}
