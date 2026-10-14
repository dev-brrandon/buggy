package de.legitserver.assistant.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessDenialReason;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessPolicy;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentDraft;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.policy.IllegalKnowledgeStatusTransitionException;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import de.legitserver.assistant.knowledge.policy.KnowledgePublicationWorkflow;
import de.legitserver.assistant.knowledge.repository.KnowledgeDocumentNotFoundException;
import de.legitserver.assistant.knowledge.repository.KnowledgeRepository;
import de.legitserver.assistant.knowledge.repository.KnowledgeRevisionConflictException;
import de.legitserver.assistant.knowledge.repository.KnowledgeStatusConflictException;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchHit;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchPort;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus;
import de.legitserver.assistant.knowledge.service.KnowledgeCatalogService;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Phase-7 offline verification of Knowledge invariants and fail-closed retrieval semantics. */
public final class Phase7KnowledgeSelfTest {
    private static final ActorId OWNER = new ActorId("actor.phase7.owner");
    private static final Instant NOW = Instant.parse("2026-10-04T08:00:00Z");
    private static final KnowledgeSourceAttribution SOURCE =
            new KnowledgeSourceAttribution("PROJECT_FILE", "foundation/masterplan", "Foundation Masterplan");

    private Phase7KnowledgeSelfTest() {}

    public static void main(String[] args) {
        verifyCanonicalDomainSet();
        verifyAclPolicy();
        verifyPublicationWorkflow();
        verifyVersionedCatalogAndPublishedOnlyRetrieval();
        verifyNoReliableResult();
        verifyHardLimits();
        System.out.println("PHASE7_KNOWLEDGE_SELF_TEST: PASS");
    }

    private static void verifyCanonicalDomainSet() {
        require(KnowledgeDomain.values().length == 9, "nine canonical Knowledge domains");
        require(Set.of(KnowledgeDomain.values()).contains(KnowledgeDomain.SECURITY), "SECURITY domain present");
        require(Set.of(KnowledgeDomain.values()).contains(KnowledgeDomain.MINECRAFT_ARCHITECTURE),
                "MINECRAFT_ARCHITECTURE domain present");
    }

    private static void verifyAclPolicy() {
        KnowledgeDocument document = document(
                KnowledgeDocumentStatus.PUBLISHED,
                DataClassification.CONFIDENTIAL,
                KnowledgeDomain.SERVER_OPERATIONS,
                new KnowledgeAcl(Set.of("networkcore.docs.technical")));
        KnowledgeAccessPolicy policy = new KnowledgeAccessPolicy();
        KnowledgeAccessContext allowed = context(
                true,
                Set.of("networkcore.docs.technical"),
                Set.of(KnowledgeDomain.SERVER_OPERATIONS),
                DataClassification.CONFIDENTIAL);
        require(policy.evaluate(document, allowed).allowed(), "authorized ACL must allow");

        KnowledgeAccessContext unauthorizedQuery = context(
                false,
                Set.of("networkcore.docs.technical"),
                Set.of(KnowledgeDomain.SERVER_OPERATIONS),
                DataClassification.SECRET);
        require(policy.evaluate(document, unauthorizedQuery).denialReason()
                        == KnowledgeAccessDenialReason.QUERY_NOT_AUTHORIZED,
                "query authorization denial");

        KnowledgeAccessContext wrongDomain = context(
                true,
                Set.of("networkcore.docs.technical"),
                Set.of(KnowledgeDomain.PANEL),
                DataClassification.SECRET);
        require(policy.evaluate(document, wrongDomain).denialReason() == KnowledgeAccessDenialReason.DOMAIN_DENIED,
                "domain denial");

        KnowledgeAccessContext classificationTooLow = context(
                true,
                Set.of("networkcore.docs.technical"),
                Set.of(KnowledgeDomain.SERVER_OPERATIONS),
                DataClassification.INTERNAL);
        require(policy.evaluate(document, classificationTooLow).denialReason()
                        == KnowledgeAccessDenialReason.CLASSIFICATION_DENIED,
                "classification denial");

        KnowledgeAccessContext permissionMissing = context(
                true,
                Set.of(),
                Set.of(KnowledgeDomain.SERVER_OPERATIONS),
                DataClassification.SECRET);
        require(policy.evaluate(document, permissionMissing).denialReason()
                        == KnowledgeAccessDenialReason.DOCUMENT_PERMISSION_DENIED,
                "document permission denial");
    }

    private static void verifyPublicationWorkflow() {
        KnowledgePublicationWorkflow workflow = new KnowledgePublicationWorkflow();
        workflow.requireTransition(KnowledgeDocumentStatus.DRAFT, KnowledgeDocumentStatus.REVIEW);
        workflow.requireTransition(KnowledgeDocumentStatus.REVIEW, KnowledgeDocumentStatus.PUBLISHED);
        workflow.requireTransition(KnowledgeDocumentStatus.PUBLISHED, KnowledgeDocumentStatus.ARCHIVED);
        expectFailure(
                IllegalKnowledgeStatusTransitionException.class,
                () -> workflow.requireTransition(KnowledgeDocumentStatus.DRAFT, KnowledgeDocumentStatus.PUBLISHED));
        expectFailure(
                IllegalKnowledgeStatusTransitionException.class,
                () -> workflow.requireTransition(KnowledgeDocumentStatus.ARCHIVED, KnowledgeDocumentStatus.DRAFT));
    }

    private static void verifyVersionedCatalogAndPublishedOnlyRetrieval() {
        FakeKnowledgeStore store = new FakeKnowledgeStore();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        KnowledgeCatalogService service =
                new KnowledgeCatalogService(store, store, new KnowledgePublicationWorkflow(), clock);
        KnowledgeDocument created = service.create(
                new KnowledgeDocumentDraft(
                        KnowledgeDomain.TROUBLESHOOTING,
                        "Proxy startup failure",
                        DataClassification.INTERNAL,
                        KnowledgeAcl.openWithinPolicy(),
                        "Velocity startup fails when the forwarding secret is missing.",
                        Set.of("velocity", "startup"),
                        Map.of("component", "networkcore"),
                        SOURCE),
                OWNER);
        require(created.status() == KnowledgeDocumentStatus.DRAFT, "new documents start as DRAFT");
        require(created.currentRevision() == 1, "initial revision is 1");

        KnowledgeAccessContext access = context(
                true,
                Set.of(),
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                DataClassification.INTERNAL);
        KnowledgeSearchQuery query = new KnowledgeSearchQuery(
                "forwarding secret",
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                Set.of("velocity"),
                Map.of("component", "networkcore"),
                10);
        require(service.search(query, access).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "DRAFT must not be standard retrieval");

        KnowledgeDocument review = service.submitForReview(created.documentId(), 1, OWNER);
        require(review.status() == KnowledgeDocumentStatus.REVIEW, "review transition");
        KnowledgeDocument published = service.publish(created.documentId(), 1, OWNER);
        require(published.status() == KnowledgeDocumentStatus.PUBLISHED, "publish transition");
        require(published.validFrom() != null, "published document has validFrom");

        KnowledgeSearchResult firstSearch = service.search(query, access);
        require(firstSearch.status() == KnowledgeSearchStatus.RESULTS, "published document searchable");
        require(firstSearch.hits().size() == 1, "one published hit");
        require(firstSearch.hits().getFirst().source().sourceRef().equals("foundation/masterplan"),
                "source attribution retained");

        KnowledgeRevision original = store.findRevision(created.documentId(), 1).orElseThrow();
        KnowledgeDocument revised = service.addRevision(
                created.documentId(),
                1,
                "Velocity startup fails when forwarding secret configuration is absent. Restart only after validation.",
                Set.of("velocity", "startup"),
                Map.of("component", "networkcore"),
                new KnowledgeSourceAttribution("PROJECT_FILE", "runbook/velocity", "Velocity Runbook"),
                OWNER);
        require(revised.currentRevision() == 2, "revision advanced");
        require(revised.status() == KnowledgeDocumentStatus.DRAFT, "new revision demotes published head to DRAFT");
        require(revised.validFrom() == null, "revised draft clears validFrom");
        require(store.findRevision(created.documentId(), 1).orElseThrow().equals(original),
                "old revision remains immutable");
        require(service.search(query, access).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "unreviewed revision is not exposed");

        service.submitForReview(created.documentId(), 2, OWNER);
        service.publish(created.documentId(), 2, OWNER);
        KnowledgeSearchResult secondSearch = service.search(
                new KnowledgeSearchQuery("validation", Set.of(), Set.of(), Map.of(), 10), access);
        require(secondSearch.status() == KnowledgeSearchStatus.RESULTS, "republished revision searchable");
        require(secondSearch.hits().getFirst().revision() == 2, "search returns current revision");

        expectFailure(
                KnowledgeRevisionConflictException.class,
                () -> service.addRevision(
                        created.documentId(),
                        1,
                        "stale revision",
                        Set.of(),
                        Map.of(),
                        SOURCE,
                        OWNER));
    }

    private static void verifyNoReliableResult() {
        FakeKnowledgeStore store = new FakeKnowledgeStore();
        KnowledgeCatalogService service = new KnowledgeCatalogService(
                store,
                store,
                new KnowledgePublicationWorkflow(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        KnowledgeSearchResult result = service.search(
                new KnowledgeSearchQuery("does-not-exist", Set.of(), Set.of(), Map.of(), 10),
                context(true, Set.of(), Set.of(KnowledgeDomain.SECURITY), DataClassification.SECRET));
        require(result.status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT, "empty retrieval is explicit no-result");
        require(result.hits().isEmpty(), "no-result contains no invented hit");

        KnowledgeSearchResult denied = service.search(
                new KnowledgeSearchQuery(null, Set.of(), Set.of(), Map.of(), 10),
                context(false, Set.of(), Set.of(KnowledgeDomain.SECURITY), DataClassification.SECRET));
        require(denied.status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT, "unauthorized retrieval yields no data");
    }

    private static void verifyHardLimits() {
        expectFailure(
                IllegalArgumentException.class,
                () -> new KnowledgeSearchQuery("x", Set.of(), Set.of(), Map.of(), KnowledgeLimits.MAX_SEARCH_LIMIT + 1));
        expectFailure(
                IllegalArgumentException.class,
                () -> KnowledgeValidation.tags(Set.of("invalid tag with spaces")));
        expectFailure(
                IllegalArgumentException.class,
                () -> new KnowledgeRevision(
                        KnowledgeDocumentId.random(),
                        1,
                        "content",
                        "0".repeat(64),
                        Set.of(),
                        Map.of(),
                        SOURCE,
                        OWNER,
                        NOW));
    }

    private static KnowledgeDocument document(
            KnowledgeDocumentStatus status,
            DataClassification classification,
            KnowledgeDomain domain,
            KnowledgeAcl acl) {
        return new KnowledgeDocument(
                KnowledgeDocumentId.random(),
                domain,
                "ACL probe",
                classification,
                status,
                1,
                status == KnowledgeDocumentStatus.PUBLISHED ? NOW : null,
                OWNER,
                NOW,
                OWNER,
                NOW,
                acl);
    }

    private static KnowledgeAccessContext context(
            boolean authorized,
            Set<String> permissions,
            Set<KnowledgeDomain> domains,
            DataClassification maximumClassification) {
        return new KnowledgeAccessContext(OWNER, authorized, permissions, domains, maximumClassification);
    }

    private static void expectFailure(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
            throw new IllegalStateException("Expected failure: " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new IllegalStateException("Unexpected failure type: " + failure, failure);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static final class FakeKnowledgeStore implements KnowledgeRepository, KnowledgeSearchPort {
        private final Map<KnowledgeDocumentId, KnowledgeDocument> documents = new LinkedHashMap<>();
        private final Map<KnowledgeDocumentId, Map<Long, KnowledgeRevision>> revisions = new HashMap<>();
        private final KnowledgeAccessPolicy accessPolicy = new KnowledgeAccessPolicy();

        @Override
        public void create(KnowledgeDocument document, KnowledgeRevision initialRevision) {
            if (documents.putIfAbsent(document.documentId(), document) != null) {
                throw new IllegalStateException("duplicate document");
            }
            revisions.put(document.documentId(), new LinkedHashMap<>(Map.of(initialRevision.revision(), initialRevision)));
        }

        @Override
        public Optional<KnowledgeDocument> findById(KnowledgeDocumentId documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public Optional<KnowledgeRevision> findRevision(KnowledgeDocumentId documentId, long revision) {
            Map<Long, KnowledgeRevision> values = revisions.get(documentId);
            return values == null ? Optional.empty() : Optional.ofNullable(values.get(revision));
        }

        @Override
        public KnowledgeDocument appendRevision(
                KnowledgeRevision revision,
                long expectedCurrentRevision,
                ActorId updatedBy,
                Instant updatedAt) {
            KnowledgeDocument current = requireDocument(revision.documentId());
            if (current.currentRevision() != expectedCurrentRevision) {
                throw new KnowledgeRevisionConflictException(revision.documentId(), expectedCurrentRevision);
            }
            if (current.status() == KnowledgeDocumentStatus.ARCHIVED) {
                throw new IllegalStateException("archived");
            }
            if (revision.revision() != expectedCurrentRevision + 1) {
                throw new IllegalArgumentException("invalid next revision");
            }
            Map<Long, KnowledgeRevision> values = revisions.get(revision.documentId());
            if (values.putIfAbsent(revision.revision(), revision) != null) {
                throw new IllegalStateException("duplicate revision");
            }
            KnowledgeDocument updated = new KnowledgeDocument(
                    current.documentId(),
                    current.domain(),
                    current.title(),
                    current.classification(),
                    KnowledgeDocumentStatus.DRAFT,
                    revision.revision(),
                    null,
                    current.createdBy(),
                    current.createdAt(),
                    updatedBy,
                    updatedAt,
                    current.acl());
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
            KnowledgeDocument current = requireDocument(documentId);
            if (current.currentRevision() != expectedCurrentRevision) {
                throw new KnowledgeRevisionConflictException(documentId, expectedCurrentRevision);
            }
            if (current.status() != expectedStatus) {
                throw new KnowledgeStatusConflictException(documentId, expectedStatus);
            }
            KnowledgeDocument updated = new KnowledgeDocument(
                    current.documentId(),
                    current.domain(),
                    current.title(),
                    current.classification(),
                    newStatus,
                    current.currentRevision(),
                    validFrom,
                    current.createdBy(),
                    current.createdAt(),
                    updatedBy,
                    updatedAt,
                    current.acl());
            documents.put(updated.documentId(), updated);
            return updated;
        }

        @Override
        public KnowledgeSearchResult search(KnowledgeSearchQuery query, KnowledgeAccessContext accessContext) {
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (KnowledgeDocument document : documents.values()) {
                if (document.status() != KnowledgeDocumentStatus.PUBLISHED) continue;
                if (!query.domains().isEmpty() && !query.domains().contains(document.domain())) continue;
                if (!accessPolicy.evaluate(document, accessContext).allowed()) continue;
                KnowledgeRevision revision = findRevision(document.documentId(), document.currentRevision()).orElseThrow();
                if (query.text() != null) {
                    String needle = query.text().toLowerCase(Locale.ROOT);
                    String haystack = (document.title() + " " + revision.content()).toLowerCase(Locale.ROOT);
                    if (!haystack.contains(needle)) continue;
                }
                if (!revision.tags().containsAll(query.tags())) continue;
                if (!metadataContains(revision.metadata(), query.metadata())) continue;
                hits.add(new KnowledgeSearchHit(
                        document.documentId(),
                        revision.revision(),
                        document.domain(),
                        document.title(),
                        document.classification(),
                        revision.content(),
                        revision.contentSha256(),
                        revision.tags(),
                        revision.metadata(),
                        revision.source(),
                        query.text() == null ? 0.0d : 1.0d));
            }
            hits.sort(Comparator.comparing(hit -> hit.documentId().toString()));
            if (hits.size() > query.limit()) hits = new ArrayList<>(hits.subList(0, query.limit()));
            return hits.isEmpty() ? KnowledgeSearchResult.noReliableResult() : KnowledgeSearchResult.results(hits);
        }

        private KnowledgeDocument requireDocument(KnowledgeDocumentId id) {
            KnowledgeDocument document = documents.get(id);
            if (document == null) throw new KnowledgeDocumentNotFoundException(id);
            return document;
        }

        private static boolean metadataContains(Map<String, String> document, Map<String, String> query) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!entry.getValue().equals(document.get(entry.getKey()))) return false;
            }
            return true;
        }
    }
}
