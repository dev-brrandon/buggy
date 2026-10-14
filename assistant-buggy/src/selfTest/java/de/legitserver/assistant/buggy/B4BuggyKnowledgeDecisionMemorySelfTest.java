package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAccess;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAnnotationRevision;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeArea;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConfidence;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConflictStatus;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeContentPolicy;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeDraft;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProfile;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProvenance;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeSearchRequest;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeState;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeTopic;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeVisibility;
import de.legitserver.assistant.buggy.knowledge.BuggyOwnerDecisionDraft;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.buggy.repository.BuggyKnowledgeAnnotationRepository;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyKnowledgeService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessPolicy;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentDraft;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.policy.KnowledgePublicationWorkflow;
import de.legitserver.assistant.knowledge.repository.KnowledgeRepository;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchHit;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchPort;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.knowledge.service.KnowledgeCatalogService;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** B4 offline verification of Knowledge binding, Owner Knowledge, Decision Memory and stale/conflict handling. */
public final class B4BuggyKnowledgeDecisionMemorySelfTest {
    private static final ActorId OWNER = new ActorId("actor.buggy.owner");
    private static final ActorId OTHER = new ActorId("actor.other");
    private static final ActorId PROVISIONER = new ActorId("actor.provisioner");
    private static final Instant NOW = Instant.parse("2026-10-04T20:45:00Z");
    private static final Set<String> BUGGY_READ_PERMISSIONS = Set.of(
            BuggyKnowledgeProfile.KNOWLEDGE_READ_PERMISSION,
            BuggyKnowledgeProfile.SECURITY_READ_PERMISSION);

    private B4BuggyKnowledgeDecisionMemorySelfTest() {}

    public static void main(String[] args) {
        Fixture fixture = new Fixture();
        verifyBroadProfile();
        verifyDraftProvenanceAndSecretExclusion(fixture);
        verifyOwnerKnowledgeBoundary(fixture);
        verifyDecisionMemoryAndConflictDetection(fixture);
        verifyStaleAndDisputedAnnotations(fixture);
        verifyRetrievalSecretExclusion(fixture);
        System.out.println("B4_BUGGY_KNOWLEDGE_DECISION_MEMORY_SELF_TEST: PASS");
    }

    private static void verifyBroadProfile() {
        require(BuggyKnowledgeProfile.allowedFoundationDomains().equals(Set.of(KnowledgeDomain.values())),
                "Buggy must bind every Foundation Knowledge domain");
        require(BuggyKnowledgeProfile.maximumNormalClassification() == DataClassification.RESTRICTED,
                "normal Buggy Knowledge must stop below SECRET");
        require(BuggyKnowledgeTopic.values().length >= 17, "broad Buggy topic taxonomy");
    }

    private static void verifyDraftProvenanceAndSecretExclusion(Fixture fixture) {
        KnowledgeDocument draft = fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.SOURCE_BUILD_HISTORY,
                "Build lineage",
                BuggyKnowledgeVisibility.INTERNAL,
                "Build 104 follows build 103 after the parser fix.",
                Set.of("build"),
                Map.of("component", "parser"),
                BuggyKnowledgeProvenance.SOURCE_CODE,
                "git:commit:abc123",
                "Parser source",
                BuggyKnowledgeConfidence.VERIFIED,
                "parser.default-mode",
                "strict"), OWNER);
        require(draft.status() == KnowledgeDocumentStatus.DRAFT, "Buggy may create DRAFTs only");
        KnowledgeRevision revision = fixture.repository.findRevision(draft.documentId(), 1).orElseThrow();
        require(revision.source().sourceKind().equals("SOURCE_CODE"), "provenance is persisted in Foundation revision");
        require(revision.metadata().get("buggy.confidence").equals("VERIFIED"), "confidence metadata");

        expectFailure(() -> fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.DATABASES_MIGRATIONS,
                "Unsafe credential note",
                BuggyKnowledgeVisibility.INTERNAL,
                "password=hunter2",
                Set.of(),
                Map.of(),
                BuggyKnowledgeProvenance.CONFIGURATION,
                "config:test",
                null,
                BuggyKnowledgeConfidence.HIGH,
                null,
                null), OWNER));

        expectFailure(() -> fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.SECURITY_ARCHITECTURE_POLICIES,
                "Forbidden secret class",
                BuggyKnowledgeVisibility.SECRET,
                "A non-secret placeholder is still not allowed under SECRET classification.",
                Set.of(),
                Map.of(),
                BuggyKnowledgeProvenance.DOCUMENTATION,
                "doc:secret",
                null,
                BuggyKnowledgeConfidence.HIGH,
                null,
                null), OWNER));
    }

    private static void verifyOwnerKnowledgeBoundary(Fixture fixture) {
        KnowledgeDocument ownerDraft = fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.OWNER_KNOWLEDGE,
                BuggyKnowledgeTopic.OWNER_CONTEXT,
                "Owner working preference",
                BuggyKnowledgeVisibility.OWNER_CONFIDENTIAL,
                "Prefer canonical master bundles after each completed implementation phase.",
                Set.of("owner-preference"),
                Map.of(),
                BuggyKnowledgeProvenance.OWNER_DECISION,
                "conversation:owner:42",
                "Explicit owner instruction",
                BuggyKnowledgeConfidence.VERIFIED,
                null,
                null), OWNER);
        fixture.publish(ownerDraft);

        KnowledgeSearchResult ownerResult = fixture.buggy.search(search("canonical master bundles"), access(OWNER));
        require(ownerResult.hits().stream().anyMatch(hit -> hit.documentId().equals(ownerDraft.documentId())),
                "Owner can retrieve Owner Knowledge");
        KnowledgeSearchResult otherResult = fixture.buggy.search(search("canonical master bundles"), access(OTHER));
        require(otherResult.status() == de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "non-owner context cannot retrieve Owner Knowledge");
        expectFailure(() -> fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.OWNER_KNOWLEDGE,
                BuggyKnowledgeTopic.OWNER_CONTEXT,
                "Fake owner decision",
                BuggyKnowledgeVisibility.OWNER_CONFIDENTIAL,
                "Untrusted actor attempts to assert an Owner decision.",
                Set.of(),
                Map.of(),
                BuggyKnowledgeProvenance.OWNER_DECISION,
                "conversation:other",
                null,
                BuggyKnowledgeConfidence.VERIFIED,
                null,
                null), OTHER));
    }

    private static void verifyDecisionMemoryAndConflictDetection(Fixture fixture) {
        KnowledgeDocument decision = fixture.buggy.recordOwnerDecision(new BuggyOwnerDecisionDraft(
                "Economy rule",
                BuggyKnowledgeTopic.HISTORICAL_DECISIONS_RATIONALE,
                "No Coins; economy uses Vanilla Items.",
                "network economy",
                "Trading should remain grounded in Vanilla Minecraft items.",
                "Network Owner",
                "conversation:decision:economy",
                "Owner decision",
                BuggyKnowledgeVisibility.OWNER_CONFIDENTIAL,
                "economy.medium",
                "vanilla-items"), OWNER);
        require(decision.status() == KnowledgeDocumentStatus.DRAFT,
                "Owner decision capture must still obey Foundation publication lifecycle");
        fixture.publish(decision);

        BuggyKnowledgeDraft conflicting = new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.HISTORICAL_DECISIONS_RATIONALE,
                "Candidate economy change",
                BuggyKnowledgeVisibility.INTERNAL,
                "Proposal to introduce Coins.",
                Set.of(),
                Map.of(),
                BuggyKnowledgeProvenance.ASSISTANT_INFERENCE,
                "proposal:economy",
                null,
                BuggyKnowledgeConfidence.MEDIUM,
                "economy.medium",
                "coins");
        var conflict = fixture.buggy.detectConflicts(conflicting, access(OWNER));
        require(conflict.status() == BuggyKnowledgeConflictStatus.CONFLICT, "conflicting decision is detected");
        require(conflict.conflicts().stream().anyMatch(item -> item.documentId().equals(decision.documentId())),
                "Decision Memory participates in conflict detection");

        BuggyKnowledgeDraft noKey = new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.MONITORING_OBSERVABILITY,
                "No conflict key",
                BuggyKnowledgeVisibility.INTERNAL,
                "Evidence without a semantic comparison key.",
                Set.of(),
                Map.of(),
                BuggyKnowledgeProvenance.LOG_ANALYSIS,
                "log:1",
                null,
                BuggyKnowledgeConfidence.LOW,
                null,
                null);
        require(fixture.buggy.detectConflicts(noKey, access(OWNER)).status()
                        == BuggyKnowledgeConflictStatus.NO_RELIABLE_RESULT,
                "insufficient evidence produces NO_RELIABLE_RESULT");
    }

    private static void verifyStaleAndDisputedAnnotations(Fixture fixture) {
        KnowledgeDocument doc = fixture.buggy.createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.GENERAL,
                BuggyKnowledgeTopic.OPERATIONS_RECOVERY_RUNBOOKS,
                "Proxy recovery runbook",
                BuggyKnowledgeVisibility.INTERNAL,
                "Restart the proxy only after health checks confirm dependency recovery.",
                Set.of("runbook"),
                Map.of(),
                BuggyKnowledgeProvenance.DOCUMENTATION,
                "runbook:proxy",
                null,
                BuggyKnowledgeConfidence.HIGH,
                null,
                null), OWNER);
        fixture.publish(doc);
        require(fixture.buggy.search(search("dependency recovery"), access(OWNER)).hits().stream()
                .anyMatch(hit -> hit.documentId().equals(doc.documentId())), "active Knowledge visible");

        BuggyKnowledgeAnnotationRevision stale = fixture.buggy.markStale(
                doc.documentId(), "Runbook predates proxy topology v2", "deployment:proxy-v2",
                BuggyKnowledgeConfidence.HIGH, OWNER);
        require(stale.state() == BuggyKnowledgeState.STALE && stale.revision() == 1, "stale annotation revision 1");
        require(fixture.buggy.search(search("dependency recovery"), access(OWNER)).hits().stream()
                .noneMatch(hit -> hit.documentId().equals(doc.documentId())), "stale Knowledge hidden by default");

        KnowledgeSearchResult withStale = fixture.buggy.search(new BuggyKnowledgeSearchRequest(
                "dependency recovery", Set.of(), Set.of(), Map.of(), true, false, 50), access(OWNER));
        require(withStale.hits().stream().anyMatch(hit -> hit.documentId().equals(doc.documentId())),
                "stale Knowledge remains inspectable when explicitly requested");

        KnowledgeDocument correction = fixture.buggy.createCorrectionDraft(
                doc.documentId(),
                new BuggyKnowledgeDraft(
                        BuggyKnowledgeArea.GENERAL,
                        BuggyKnowledgeTopic.OPERATIONS_RECOVERY_RUNBOOKS,
                        "Proxy recovery runbook v2",
                        BuggyKnowledgeVisibility.INTERNAL,
                        "Use dependency-aware proxy recovery sequence from topology v2.",
                        Set.of("runbook", "correction"),
                        Map.of(),
                        BuggyKnowledgeProvenance.DOCUMENTATION,
                        "source:topology-v2",
                        "Topology v2",
                        BuggyKnowledgeConfidence.VERIFIED,
                        null,
                        null),
                OWNER);
        KnowledgeRevision correctionRevision = fixture.repository.findRevision(correction.documentId(), 1).orElseThrow();
        require(correctionRevision.metadata().get("supersedes_document_id").equals(doc.documentId().value().toString()),
                "correction DRAFT links immutable superseded document");
        require(correction.status() == KnowledgeDocumentStatus.DRAFT, "correction remains a DRAFT until publication workflow");

        BuggyKnowledgeAnnotationRevision disputed = fixture.buggy.markDisputed(
                doc.documentId(), "Two current topology sources disagree", "incident:991",
                BuggyKnowledgeConfidence.MEDIUM, OWNER);
        require(disputed.revision() == 2 && disputed.previousRevision() == 1L, "annotation history is append-only");
        BuggyKnowledgeAnnotationRevision resolved = fixture.buggy.resolveAnnotation(
                doc.documentId(), "Canonical topology document verified", "source:topology-v2",
                BuggyKnowledgeConfidence.VERIFIED, OWNER);
        require(resolved.state() == BuggyKnowledgeState.ACTIVE && resolved.revision() == 3,
                "resolution appends a new ACTIVE assessment");
        require(fixture.buggy.annotationHistory(doc.documentId()).size() == 3, "annotation history retained");
    }

    private static void verifyRetrievalSecretExclusion(Fixture fixture) {
        KnowledgeDocument raw = fixture.catalog.create(new KnowledgeDocumentDraft(
                KnowledgeDomain.TROUBLESHOOTING,
                "Misclassified credential leak",
                DataClassification.INTERNAL,
                KnowledgeAcl.openWithinPolicy(),
                "password=should-never-enter-buggy-context",
                Set.of("unsafe"),
                Map.of(),
                new KnowledgeSourceAttribution("DOCUMENTATION", "unsafe:test", null)), OWNER);
        fixture.publish(raw);
        KnowledgeSearchResult result = fixture.buggy.search(search("should-never-enter"), access(OWNER));
        require(result.status() == de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "secret-like material is excluded even when upstream classification is wrong");
    }

    private static BuggyKnowledgeSearchRequest search(String text) {
        return new BuggyKnowledgeSearchRequest(text, Set.of(), Set.of(), Map.of(), false, false, 50);
    }

    private static BuggyKnowledgeAccess access(ActorId actorId) {
        return new BuggyKnowledgeAccess(actorId, true, BUGGY_READ_PERMISSIONS);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected operation to fail");
        } catch (IllegalArgumentException | SecurityException expected) {
            require(expected.getMessage() != null, "failure must provide context");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        private final InMemoryKnowledgeStore repository = new InMemoryKnowledgeStore();
        private final KnowledgeCatalogService catalog = new KnowledgeCatalogService(
                repository,
                repository,
                new KnowledgePublicationWorkflow(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        private final BuggyKnowledgeService buggy;

        private Fixture() {
            BuggyBootstrapSeed seed = BuggyBootstrapSeed.create(OWNER, PROVISIONER, NOW);
            BuggyIdentitySnapshot identity = new BuggyIdentitySnapshot(
                    seed.identityCore(), seed.ownerBinding(), seed.configuration(), 1, NOW);
            BuggyIdentityService identityService = new BuggyIdentityService(new FixedIdentityRepository(identity));
            buggy = new BuggyKnowledgeService(
                    identityService,
                    catalog,
                    repository,
                    new KnowledgeAccessPolicy(),
                    new InMemoryAnnotationRepository(),
                    new TestSecretPolicy(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private void publish(KnowledgeDocument document) {
            KnowledgeDocument reviewed = catalog.submitForReview(document.documentId(), document.currentRevision(), OWNER);
            catalog.publish(reviewed.documentId(), reviewed.currentRevision(), OWNER);
        }
    }

    private static final class FixedIdentityRepository implements BuggyIdentityRepository {
        private BuggyIdentitySnapshot snapshot;

        private FixedIdentityRepository(BuggyIdentitySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<BuggyIdentitySnapshot> find() {
            return Optional.of(snapshot);
        }

        @Override
        public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
            return snapshot;
        }

        @Override
        public BuggyIdentitySnapshot activatePersonalityProfile(String profileId, ActorId createdBy, Instant createdAt) {
            return snapshot;
        }
    }

    private static final class TestSecretPolicy implements BuggyKnowledgeContentPolicy {
        @Override
        public String requirePersistableText(String value, String field, int maxLength) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            String normalized = value.strip();
            if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " too long");
            if (containsSecretMaterial(normalized)) throw new IllegalArgumentException(field + " contains secret material");
            return normalized;
        }

        @Override
        public boolean containsSecretMaterial(String value) {
            if (value == null) return false;
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("password=") || lower.contains("api_key=") || lower.contains("authorization: bearer ");
        }
    }

    private static final class InMemoryAnnotationRepository implements BuggyKnowledgeAnnotationRepository {
        private final Map<KnowledgeDocumentId, List<BuggyKnowledgeAnnotationRevision>> history = new HashMap<>();

        @Override
        public Optional<BuggyKnowledgeAnnotationRevision> findCurrent(KnowledgeDocumentId documentId) {
            List<BuggyKnowledgeAnnotationRevision> revisions = history.get(documentId);
            return revisions == null || revisions.isEmpty() ? Optional.empty() : Optional.of(revisions.get(revisions.size() - 1));
        }

        @Override
        public List<BuggyKnowledgeAnnotationRevision> history(KnowledgeDocumentId documentId) {
            return List.copyOf(history.getOrDefault(documentId, List.of()));
        }

        @Override
        public BuggyKnowledgeAnnotationRevision append(long expectedCurrentRevision, BuggyKnowledgeAnnotationRevision revision) {
            List<BuggyKnowledgeAnnotationRevision> revisions = history.computeIfAbsent(revision.documentId(), key -> new ArrayList<>());
            if (revisions.size() != expectedCurrentRevision) throw new IllegalStateException("stale annotation revision");
            revisions.add(revision);
            return revision;
        }
    }

    private static final class InMemoryKnowledgeStore implements KnowledgeRepository, KnowledgeSearchPort {
        private final Map<KnowledgeDocumentId, KnowledgeDocument> documents = new LinkedHashMap<>();
        private final Map<KnowledgeDocumentId, Map<Long, KnowledgeRevision>> revisions = new LinkedHashMap<>();
        private final KnowledgeAccessPolicy accessPolicy = new KnowledgeAccessPolicy();

        @Override
        public void create(KnowledgeDocument document, KnowledgeRevision initialRevision) {
            if (documents.putIfAbsent(document.documentId(), document) != null) throw new IllegalStateException("duplicate document");
            revisions.put(document.documentId(), new LinkedHashMap<>(Map.of(1L, initialRevision)));
        }

        @Override
        public Optional<KnowledgeDocument> findById(KnowledgeDocumentId documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public Optional<KnowledgeRevision> findRevision(KnowledgeDocumentId documentId, long revision) {
            return Optional.ofNullable(revisions.getOrDefault(documentId, Map.of()).get(revision));
        }

        @Override
        public KnowledgeDocument appendRevision(
                KnowledgeRevision revision,
                long expectedCurrentRevision,
                ActorId updatedBy,
                Instant updatedAt) {
            KnowledgeDocument current = documents.get(revision.documentId());
            if (current.currentRevision() != expectedCurrentRevision) throw new IllegalStateException("stale revision");
            revisions.get(revision.documentId()).put(revision.revision(), revision);
            KnowledgeDocument updated = new KnowledgeDocument(
                    current.documentId(), current.domain(), current.title(), current.classification(), current.status(),
                    revision.revision(), current.validFrom(), current.createdBy(), current.createdAt(), updatedBy, updatedAt, current.acl());
            documents.put(updated.documentId(), updated);
            return updated;
        }

        @Override
        public KnowledgeDocument transitionStatus(
                KnowledgeDocumentId documentId,
                long expectedCurrentRevision,
                KnowledgeDocumentStatus expectedStatus,
                KnowledgeDocumentStatus newStatus,
                Instant validFrom,
                ActorId updatedBy,
                Instant updatedAt) {
            KnowledgeDocument current = documents.get(documentId);
            if (current.currentRevision() != expectedCurrentRevision || current.status() != expectedStatus) {
                throw new IllegalStateException("stale status transition");
            }
            KnowledgeDocument updated = new KnowledgeDocument(
                    current.documentId(), current.domain(), current.title(), current.classification(), newStatus,
                    current.currentRevision(), validFrom, current.createdBy(), current.createdAt(), updatedBy, updatedAt, current.acl());
            documents.put(documentId, updated);
            return updated;
        }

        @Override
        public KnowledgeSearchResult search(KnowledgeSearchQuery query, KnowledgeAccessContext context) {
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (KnowledgeDocument document : documents.values()) {
                if (document.status() != KnowledgeDocumentStatus.PUBLISHED) continue;
                if (!query.domains().isEmpty() && !query.domains().contains(document.domain())) continue;
                if (!accessPolicy.evaluate(document, context).allowed()) continue;
                KnowledgeRevision revision = revisions.get(document.documentId()).get(document.currentRevision());
                if (query.text() != null) {
                    String needle = query.text().toLowerCase(java.util.Locale.ROOT);
                    if (!document.title().toLowerCase(java.util.Locale.ROOT).contains(needle)
                            && !revision.content().toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
                }
                if (!revision.tags().containsAll(query.tags())) continue;
                boolean metadataMatch = query.metadata().entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(revision.metadata().get(entry.getKey())));
                if (!metadataMatch) continue;
                hits.add(new KnowledgeSearchHit(
                        document.documentId(), document.currentRevision(), document.domain(), document.title(),
                        document.classification(), revision.content(), revision.contentSha256(), revision.tags(),
                        revision.metadata(), revision.source(), 1.0d));
                if (hits.size() == query.limit()) break;
            }
            return hits.isEmpty() ? KnowledgeSearchResult.noReliableResult() : KnowledgeSearchResult.results(hits);
        }
    }
}
