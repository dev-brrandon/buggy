package de.legitserver.assistant.knowledge.service;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentDraft;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.policy.KnowledgePublicationWorkflow;
import de.legitserver.assistant.knowledge.repository.KnowledgeDocumentNotFoundException;
import de.legitserver.assistant.knowledge.repository.KnowledgeRepository;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchPort;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transaction-agnostic application service for Knowledge catalog lifecycle and safe retrieval. */
public final class KnowledgeCatalogService {
    private final KnowledgeRepository repository;
    private final KnowledgeSearchPort searchPort;
    private final KnowledgePublicationWorkflow workflow;
    private final Clock clock;

    public KnowledgeCatalogService(
            KnowledgeRepository repository,
            KnowledgeSearchPort searchPort,
            KnowledgePublicationWorkflow workflow,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public KnowledgeDocument create(KnowledgeDocumentDraft draft, ActorId actorId) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(actorId, "actorId");
        Instant now = clock.instant();
        KnowledgeDocumentId id = KnowledgeDocumentId.random();
        KnowledgeDocument document = new KnowledgeDocument(
                id,
                draft.domain(),
                draft.title(),
                draft.classification(),
                KnowledgeDocumentStatus.DRAFT,
                1,
                null,
                actorId,
                now,
                actorId,
                now,
                draft.acl());
        KnowledgeRevision revision = new KnowledgeRevision(
                id,
                1,
                draft.content(),
                KnowledgeValidation.sha256Of(draft.content()),
                draft.tags(),
                draft.metadata(),
                draft.source(),
                actorId,
                now);
        repository.create(document, revision);
        return document;
    }

    public KnowledgeDocument addRevision(
            KnowledgeDocumentId id,
            long expectedCurrentRevision,
            String content,
            Set<String> tags,
            Map<String, String> metadata,
            KnowledgeSourceAttribution source,
            ActorId actorId) {
        KnowledgeDocument current = requireDocument(id);
        if (current.status() == KnowledgeDocumentStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot revise archived Knowledge document");
        }
        long nextRevision = expectedCurrentRevision + 1;
        Instant now = clock.instant();
        KnowledgeRevision revision = new KnowledgeRevision(
                id,
                nextRevision,
                content,
                KnowledgeValidation.sha256Of(content),
                tags,
                metadata,
                source,
                actorId,
                now);
        return repository.appendRevision(revision, expectedCurrentRevision, actorId, now);
    }

    public KnowledgeDocument submitForReview(
            KnowledgeDocumentId id, long expectedCurrentRevision, ActorId actorId) {
        return transition(id, expectedCurrentRevision, KnowledgeDocumentStatus.REVIEW, actorId);
    }

    public KnowledgeDocument returnToDraft(
            KnowledgeDocumentId id, long expectedCurrentRevision, ActorId actorId) {
        return transition(id, expectedCurrentRevision, KnowledgeDocumentStatus.DRAFT, actorId);
    }

    public KnowledgeDocument publish(
            KnowledgeDocumentId id, long expectedCurrentRevision, ActorId actorId) {
        return transition(id, expectedCurrentRevision, KnowledgeDocumentStatus.PUBLISHED, actorId);
    }

    public KnowledgeDocument archive(
            KnowledgeDocumentId id, long expectedCurrentRevision, ActorId actorId) {
        return transition(id, expectedCurrentRevision, KnowledgeDocumentStatus.ARCHIVED, actorId);
    }

    public Optional<KnowledgeDocument> findDocument(KnowledgeDocumentId id) {
        return repository.findById(id);
    }

    public KnowledgeSearchResult search(KnowledgeSearchQuery query, KnowledgeAccessContext context) {
        return searchPort.search(query, context);
    }

    private KnowledgeDocument transition(
            KnowledgeDocumentId id,
            long expectedCurrentRevision,
            KnowledgeDocumentStatus target,
            ActorId actorId) {
        KnowledgeDocument current = requireDocument(id);
        workflow.requireTransition(current.status(), target);
        Instant now = clock.instant();
        Instant validFrom = target == KnowledgeDocumentStatus.PUBLISHED ? now : current.validFrom();
        if (target == KnowledgeDocumentStatus.DRAFT) validFrom = null;
        return repository.transitionStatus(
                id,
                expectedCurrentRevision,
                current.status(),
                target,
                validFrom,
                actorId,
                now);
    }

    private KnowledgeDocument requireDocument(KnowledgeDocumentId id) {
        return repository.findById(id).orElseThrow(() -> new KnowledgeDocumentNotFoundException(id));
    }
}
