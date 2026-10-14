package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.exclusive.BuggyArchitectureDecision;
import de.legitserver.assistant.buggy.exclusive.BuggyArchitectureGuardian;
import de.legitserver.assistant.buggy.exclusive.BuggyArchitectureProposal;
import de.legitserver.assistant.buggy.exclusive.BuggyChallengeMeService;
import de.legitserver.assistant.buggy.exclusive.BuggyContextRef;
import de.legitserver.assistant.buggy.exclusive.BuggyContextSwitchboard;
import de.legitserver.assistant.buggy.exclusive.BuggyContextType;
import de.legitserver.assistant.buggy.exclusive.BuggyCorrelationEvidence;
import de.legitserver.assistant.buggy.exclusive.BuggyCorrelationStatus;
import de.legitserver.assistant.buggy.exclusive.BuggyCrossSystemCorrelationService;
import de.legitserver.assistant.buggy.exclusive.BuggyDecisionProposal;
import de.legitserver.assistant.buggy.exclusive.BuggyDecisionSignificance;
import de.legitserver.assistant.buggy.exclusive.BuggyDeepInvestigationService;
import de.legitserver.assistant.buggy.exclusive.BuggyDevelopmentJournalEntryType;
import de.legitserver.assistant.buggy.exclusive.BuggyEvidenceSource;
import de.legitserver.assistant.buggy.exclusive.BuggyExclusiveAuthorityBoundary;
import de.legitserver.assistant.buggy.exclusive.BuggyExclusiveFeature;
import de.legitserver.assistant.buggy.exclusive.BuggyExclusiveFeatureCatalog;
import de.legitserver.assistant.buggy.exclusive.BuggyHistoricalComparisonService;
import de.legitserver.assistant.buggy.exclusive.BuggyHistoricalSnapshot;
import de.legitserver.assistant.buggy.exclusive.BuggyIncidentCommander;
import de.legitserver.assistant.buggy.exclusive.BuggyIncidentSeverity;
import de.legitserver.assistant.buggy.exclusive.BuggyOwnerBriefingService;
import de.legitserver.assistant.buggy.exclusive.BuggyOwnerPriorityChannel;
import de.legitserver.assistant.buggy.exclusive.BuggyPersonalDevelopmentJournalService;
import de.legitserver.assistant.buggy.exclusive.BuggyPriorityItem;
import de.legitserver.assistant.buggy.exclusive.BuggyPriorityKind;
import de.legitserver.assistant.buggy.exclusive.BuggyRootCauseHypothesis;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** B10 verifies Buggy-exclusive orchestration without introducing new authority or persistence. */
public final class B10BuggyExclusiveFeaturesSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T01:20:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ActorId OWNER = new ActorId("actor:buggy-owner");

    private B10BuggyExclusiveFeaturesSelfTest() {}

    public static void main(String[] args) {
        require(BuggyExclusiveFeatureCatalog.features().size() == 10, "exclusive feature inventory");
        require(BuggyExclusiveFeatureCatalog.features().contains(BuggyExclusiveFeature.DEEP_INVESTIGATION), "deep investigation catalogued");
        require(BuggyExclusiveAuthorityBoundary.readCapabilities().size() == BuggyEvidenceSource.values().length,
                "all B10 evidence uses existing B3 read capabilities");

        BuggyContextSwitchboard contexts = new BuggyContextSwitchboard();
        BuggyContextRef network = new BuggyContextRef(BuggyContextType.NETWORK, "network:legit", "Legit Network");
        BuggyContextRef incident = new BuggyContextRef(BuggyContextType.INCIDENT, "incident:42", "Incident 42");
        contexts.switchTo(network);
        contexts.switchTo(incident);
        require(contexts.current().equals(incident), "context switching current");
        require(contexts.history().equals(List.of(network)), "context switching history");

        BuggyPriorityItem normal = new BuggyPriorityItem("priority:normal", BuggyPriorityKind.SELF_DEVELOPMENT_PROPOSAL,
                BuggyAttentionLevel.NOTICE, "Self-development proposal ready", "selfdev:1", network, NOW.minusSeconds(60));
        BuggyPriorityItem critical = new BuggyPriorityItem("priority:critical", BuggyPriorityKind.INCIDENT,
                BuggyAttentionLevel.CRITICAL, "Production incident needs Owner", "incident:42", incident, NOW);
        List<BuggyPriorityItem> priority = new BuggyOwnerPriorityChannel().prioritize(List.of(normal, critical), 10);
        require(priority.getFirst().equals(critical), "critical priority first");

        BuggyCorrelationEvidence deploy = new BuggyCorrelationEvidence("ev:deploy", BuggyEvidenceSource.DEPLOYMENT,
                "build changed", 9500, Map.of("build", "dev.18"), NOW.minusSeconds(120));
        BuggyCorrelationEvidence log = new BuggyCorrelationEvidence("ev:log", BuggyEvidenceSource.LOG,
                "errors began after build", 9000, Map.of("error", "timeout"), NOW.minusSeconds(90));
        BuggyRootCauseHypothesis hypothesis = new BuggyRootCauseHypothesis("hyp:1", "Deployment introduced timeout regression",
                8800, List.of("ev:deploy", "ev:log"), List.of());
        var correlation = new BuggyCrossSystemCorrelationService(CLOCK)
                .correlate("lobby timeout", List.of(deploy, log), List.of(hypothesis));
        require(correlation.status() == BuggyCorrelationStatus.EVIDENCE_BACKED, "cross-system evidence result");
        expectFailure(() -> new BuggyCrossSystemCorrelationService(CLOCK)
                .correlate("bad", List.of(deploy), List.of(new BuggyRootCauseHypothesis(
                        "hyp:single", "single source claim", 5000, List.of("ev:deploy"), List.of()))),
                "single-source hypothesis rejected");

        BuggyTaskId taskId = BuggyTaskId.random();
        BuggyTaskRevision task = new BuggyTaskRevision(taskId, 1, null, "Resolve timeout", "Investigate evidence",
                BuggyTaskStatus.WAITING_APPROVAL, BuggyAttentionLevel.ACTION_REQUIRED, 70, "Owner approval required", OWNER, NOW);
        BuggyActivityEntry activity = new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.AUTOMATION_ACTED,
                BuggyAttentionLevel.NOTICE, taskId, "auto:diag", "Collected diagnostic evidence", Map.of("result", "ok"), OWNER, NOW);
        var briefing = new BuggyOwnerBriefingService(CLOCK)
                .create(List.of(activity), List.of(task), priority, List.of(), List.of("Timeout correlates with deployment"));
        require(!briefing.whatHappened().isEmpty(), "briefing what happened");
        require(!briefing.handledAutomatically().isEmpty(), "briefing autonomous work");
        require(!briefing.needsOwnerDecision().isEmpty(), "briefing owner decision");

        BuggyArchitectureDecision decision = new BuggyArchitectureDecision("decision:security-authority", "network-wide",
                "NetworkCore remains authoritative", Map.of("securityAuthority", "NetworkCore"), Map.of("assistantSelfApproval", "true"));
        BuggyArchitectureProposal invalidProposal = new BuggyArchitectureProposal("proposal:1", "assistant",
                "Move approval into Buggy", Map.of("securityAuthority", "Buggy", "assistantSelfApproval", "true"));
        var architectureReview = new BuggyArchitectureGuardian().review(invalidProposal, List.of(decision));
        require(!architectureReview.compliant() && architectureReview.findings().size() == 2, "architecture violations flagged");

        var incidentPlan = new BuggyIncidentCommander(CLOCK).prepare("incident:42", BuggyIncidentSeverity.SEV1,
                List.of("collect logs", "compare metrics"), List.of("prepare rollback request"));
        require(incidentPlan.reduceSideActivities(), "incident commander reduces side activities");
        require(incidentPlan.ownerEscalationRequired(), "sev1 owner escalation");

        BuggyHistoricalSnapshot before = new BuggyHistoricalSnapshot("snap:before", "lobby", NOW.minusSeconds(3600),
                Map.of("build", "17", "errors", "0"));
        BuggyHistoricalSnapshot after = new BuggyHistoricalSnapshot("snap:after", "lobby", NOW,
                Map.of("build", "18", "errors", "12"));
        var comparison = new BuggyHistoricalComparisonService().compare(before, after);
        require(comparison.changedCount() == 2, "historical changes detected");

        BuggyDecisionProposal significant = new BuggyDecisionProposal("decision:new-cache", "Replace cache layer",
                BuggyDecisionSignificance.SIGNIFICANT, List.of("latency will improve"));
        var challenge = new BuggyChallengeMeService().challenge(significant,
                List.of("Migration may increase failure modes"), List.of("Rollback path not proven"), List.of("What is the measured baseline?"));
        require(challenge.counterarguments().size() == 1, "challenge me counterargument");
        expectFailure(() -> new BuggyChallengeMeService().challenge(significant, List.of(), List.of(), List.of()),
                "significant decision cannot receive empty challenge");

        var investigation = new BuggyDeepInvestigationService(CLOCK).complete("investigation:42", incident, correlation,
                comparison, architectureReview, List.of("Is database saturation causal?"), List.of("inspect DB wait metrics"));
        require(investigation.correlation().status() == BuggyCorrelationStatus.EVIDENCE_BACKED, "deep investigation evidence retained");

        BuggyPersonalitySeed seed = BuggyPersonalitySeed.canonical();
        BuggyPersonalityRevision initial = BuggyPersonalityRevision.initial(seed, OWNER, NOW.minusSeconds(500));
        PersonalityMemory personalities = new PersonalityMemory(seed, initial);
        BuggyActivityEntry failure1 = new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.AUTOMATION_FAILED,
                BuggyAttentionLevel.NOTICE, null, "auto:test", "Repeated timeout", Map.of("attempt", "1"), OWNER, NOW.minusSeconds(10));
        BuggyActivityEntry failure2 = new BuggyActivityEntry(UUID.randomUUID(), BuggyActivityType.AUTOMATION_FAILED,
                BuggyAttentionLevel.NOTICE, null, "auto:test", "Repeated timeout", Map.of("attempt", "2"), OWNER, NOW.minusSeconds(5));
        BuggyPersonalDevelopmentJournalService journal = new BuggyPersonalDevelopmentJournalService(
                personalities, new EmptySelfDev(), new ActivityMemory(List.of(failure1, failure2)));
        var journalEntries = journal.entries(20);
        require(journalEntries.size() == 2, "journal derives personality and recurring failure history");
        require(journalEntries.stream().anyMatch(e -> e.type() == BuggyDevelopmentJournalEntryType.PERSONALITY_CHANGE),
                "journal personality entry type");
        require(journalEntries.stream().anyMatch(e -> e.type() == BuggyDevelopmentJournalEntryType.RECURRING_MISTAKE),
                "journal recurring mistake type");

        System.out.println("B10_BUGGY_EXCLUSIVE_FEATURES_SELF_TEST: PASS");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new IllegalStateException("B10 self-test expected failure: " + message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("B10 exclusive features self-test failed: " + message);
    }

    private static final class PersonalityMemory implements BuggyPersonalityRepository {
        private final BuggyPersonalitySnapshot snapshot;
        private PersonalityMemory(BuggyPersonalitySeed seed, BuggyPersonalityRevision revision) {
            snapshot = new BuggyPersonalitySnapshot(seed, revision, revision.createdAt());
        }
        @Override public Optional<BuggyPersonalitySnapshot> find() { return Optional.of(snapshot); }
        @Override public BuggyPersonalitySnapshot initialize(BuggyPersonalitySeed seed, BuggyPersonalityRevision initialRevision) { return snapshot; }
        @Override public Optional<BuggyPersonalityRevision> findRevision(long revision) { return revision == snapshot.current().revision() ? Optional.of(snapshot.current()) : Optional.empty(); }
        @Override public Optional<BuggyPersonalityObservation> findObservation(String observationId) { return Optional.empty(); }
        @Override public List<BuggyPersonalityRevision> history() { return List.of(snapshot.current()); }
        @Override public List<BuggyPersonalityObservation> observations() { return List.of(); }
        @Override public BuggyPersonalityObservation appendObservation(BuggyPersonalityObservation observation) { throw new UnsupportedOperationException(); }
        @Override public BuggyPersonalitySnapshot appendRevision(long expectedCurrentRevision, BuggyPersonalityRevision revision) { throw new UnsupportedOperationException(); }
    }


    private static final class ActivityMemory implements BuggyActivityRepository {
        private final List<BuggyActivityEntry> entries;
        private ActivityMemory(List<BuggyActivityEntry> entries) { this.entries = List.copyOf(entries); }
        @Override public void append(BuggyActivityEntry entry) { throw new UnsupportedOperationException(); }
        @Override public List<BuggyActivityEntry> recent(int limit) { return entries.stream().limit(limit).toList(); }
    }

    private static final class EmptySelfDev implements BuggySelfDevelopmentRepository {
        @Override public Optional<BuggySelfDevelopmentRevision> findCurrent(BuggySelfDevelopmentId itemId) { return Optional.empty(); }
        @Override public List<BuggySelfDevelopmentRevision> history(BuggySelfDevelopmentId itemId) { return List.of(); }
        @Override public List<BuggySelfDevelopmentRevision> listCurrentItems(int limit) { return List.of(); }
        @Override public BuggySelfDevelopmentRevision append(long expectedCurrentRevision, BuggySelfDevelopmentRevision revision) { throw new UnsupportedOperationException(); }
        @Override public void appendEvidence(BuggySelfDevelopmentEvidence evidence) { throw new UnsupportedOperationException(); }
        @Override public List<BuggySelfDevelopmentEvidence> evidence(BuggySelfDevelopmentId itemId) { return List.of(); }
    }
}
