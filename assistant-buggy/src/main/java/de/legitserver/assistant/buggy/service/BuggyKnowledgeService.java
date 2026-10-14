package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAccess;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAnnotationRevision;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeArea;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeCanonicalizer;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConfidence;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConflict;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConflictReport;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConflictStatus;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeContentPolicy;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeDraft;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProfile;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProvenance;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeRecord;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeSearchRequest;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeState;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeTopic;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeVisibility;
import de.legitserver.assistant.buggy.knowledge.BuggyOwnerDecisionDraft;
import de.legitserver.assistant.buggy.repository.BuggyKnowledgeAnnotationRepository;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessPolicy;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentDraft;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.repository.KnowledgeRepository;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchHit;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus;
import de.legitserver.assistant.knowledge.service.KnowledgeCatalogService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B4 Buggy binding over the accepted Foundation Knowledge catalog.
 * Knowledge content/revisions remain Foundation-owned; only Buggy assessment annotations are product state.
 */
public final class BuggyKnowledgeService {
    private static final String META_PROFILE = "buggy.profile";
    private static final String META_AREA = "buggy.area";
    private static final String META_TOPIC = "buggy.topic";
    private static final String META_VISIBILITY = "buggy.visibility";
    private static final String META_CONFIDENCE = "buggy.confidence";
    private static final String META_CONFLICT_KEY = "buggy.conflict_key";
    private static final String META_CONFLICT_VALUE = "buggy.conflict_value";
    private static final String META_DECISION_SCOPE = "decision.scope";
    private static final String META_DECISION_AUTHORITY = "decision.authority";

    private final BuggyIdentityService identityService;
    private final KnowledgeCatalogService knowledge;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeAccessPolicy accessPolicy;
    private final BuggyKnowledgeAnnotationRepository annotations;
    private final BuggyKnowledgeContentPolicy contentPolicy;
    private final Clock clock;

    public BuggyKnowledgeService(
            BuggyIdentityService identityService,
            KnowledgeCatalogService knowledge,
            KnowledgeRepository knowledgeRepository,
            KnowledgeAccessPolicy accessPolicy,
            BuggyKnowledgeAnnotationRepository annotations,
            BuggyKnowledgeContentPolicy contentPolicy,
            Clock clock) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        this.knowledgeRepository = Objects.requireNonNull(knowledgeRepository, "knowledgeRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.annotations = Objects.requireNonNull(annotations, "annotations");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public KnowledgeDocument createDraft(BuggyKnowledgeDraft draft, ActorId actorId) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(actorId, "actorId");
        BuggyIdentitySnapshot identity = verifyKnowledgeProfile();
        validateDraftBoundary(draft, identity, actorId);

        LinkedHashSet<String> tags = new LinkedHashSet<>(draft.tags());
        tags.add("buggy");
        tags.add("buggy-knowledge");
        tags.add("buggy-area-" + draft.area().name().toLowerCase(Locale.ROOT).replace('_', '-'));

        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : draft.metadata().entrySet()) {
            if (isReservedMetadata(entry.getKey())
                    && !(draft.area() == BuggyKnowledgeArea.DECISION_MEMORY
                            && draft.provenance() == BuggyKnowledgeProvenance.OWNER_DECISION
                            && entry.getKey().startsWith("decision."))) {
                throw new IllegalArgumentException("Caller metadata may not override reserved Buggy Knowledge keys");
            }
            metadata.put(entry.getKey(), requireSecretFree(entry.getValue(), "metadataValue", 4096));
        }
        metadata.put(META_PROFILE, BuggyKnowledgeProfile.PROFILE_ID);
        metadata.put(META_AREA, draft.area().name());
        metadata.put(META_TOPIC, draft.topic().name());
        metadata.put(META_VISIBILITY, draft.visibility().name());
        metadata.put(META_CONFIDENCE, draft.confidence().name());
        if (draft.conflictKey() != null) {
            metadata.put(META_CONFLICT_KEY, draft.conflictKey());
            metadata.put(META_CONFLICT_VALUE, requireSecretFree(draft.conflictValue(), "conflictValue", 1024));
        }

        Set<String> requiredPermissions = aclPermissions(draft.area(), draft.visibility());
        KnowledgeDocumentDraft foundationDraft = new KnowledgeDocumentDraft(
                draft.topic().foundationDomain(),
                requireSecretFree(draft.title(), "title", 256),
                draft.visibility().foundationClassification(),
                new KnowledgeAcl(requiredPermissions),
                requireSecretFree(draft.content(), "content", 1_000_000),
                tags,
                metadata,
                new KnowledgeSourceAttribution(
                        draft.provenance().name(),
                        requireSecretFree(draft.sourceRef(), "sourceRef", 1024),
                        draft.sourceLabel() == null ? null : requireSecretFree(draft.sourceLabel(), "sourceLabel", 256)));
        return knowledge.create(foundationDraft, actorId);
    }

    public KnowledgeDocument recordOwnerDecision(BuggyOwnerDecisionDraft decision, ActorId ownerActorId) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(ownerActorId, "ownerActorId");
        BuggyIdentitySnapshot identity = verifyKnowledgeProfile();
        requireOwner(identity, ownerActorId);
        String content = "Decision: " + requireSecretFree(decision.decision(), "decision", 8192)
                + "\nScope: " + requireSecretFree(decision.scope(), "scope", 1024)
                + "\nRationale: " + requireSecretFree(decision.rationale(), "rationale", 8192)
                + "\nAuthority: " + requireSecretFree(decision.authority(), "authority", 512);
        Map<String, String> metadata = Map.of(
                META_DECISION_SCOPE, decision.scope(),
                META_DECISION_AUTHORITY, decision.authority());
        return createDraft(new BuggyKnowledgeDraft(
                BuggyKnowledgeArea.DECISION_MEMORY,
                decision.topic(),
                decision.title(),
                decision.visibility(),
                content,
                Set.of("owner-decision", "decision-memory"),
                metadata,
                BuggyKnowledgeProvenance.OWNER_DECISION,
                decision.sourceRef(),
                decision.sourceLabel(),
                BuggyKnowledgeConfidence.VERIFIED,
                decision.conflictKey(),
                decision.conflictValue()), ownerActorId);
    }

    public Optional<BuggyKnowledgeRecord> read(KnowledgeDocumentId documentId, BuggyKnowledgeAccess access) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(access, "access");
        BuggyIdentitySnapshot identity = verifyKnowledgeProfile();
        Optional<KnowledgeDocument> found = knowledge.findDocument(documentId);
        if (found.isEmpty()) return Optional.empty();
        KnowledgeDocument document = found.orElseThrow();
        KnowledgeAccessContext context = foundationAccess(access, Set.of(document.domain()));
        if (!accessPolicy.evaluate(document, context).allowed()) return Optional.empty();
        KnowledgeRevision revision = knowledgeRepository.findRevision(documentId, document.currentRevision()).orElseThrow();
        BuggyKnowledgeArea area = areaOf(revision.metadata());
        BuggyKnowledgeVisibility visibility = visibilityOf(revision.metadata(), document.classification());
        if (!productAccessAllowed(identity, access, area, visibility)) return Optional.empty();
        if (containsSecretMaterial(document, revision)) return Optional.empty();
        BuggyKnowledgeState state = annotations.findCurrent(documentId)
                .map(BuggyKnowledgeAnnotationRevision::state)
                .orElse(BuggyKnowledgeState.ACTIVE);
        return Optional.of(new BuggyKnowledgeRecord(
                document, revision, area, visibility, confidenceOf(revision.metadata()), state));
    }

    public KnowledgeSearchResult search(BuggyKnowledgeSearchRequest request, BuggyKnowledgeAccess access) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(access, "access");
        BuggyIdentitySnapshot identity = verifyKnowledgeProfile();
        Set<de.legitserver.assistant.knowledge.model.KnowledgeDomain> domains = request.topics().isEmpty()
                ? BuggyKnowledgeProfile.allowedFoundationDomains()
                : request.topics().stream().map(BuggyKnowledgeTopic::foundationDomain).collect(Collectors.toUnmodifiableSet());
        KnowledgeSearchResult result = knowledge.search(
                new KnowledgeSearchQuery(request.text(), domains, request.tags(), request.metadata(), 100),
                foundationAccess(access, domains));
        if (result.status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT) return result;

        List<KnowledgeSearchHit> filtered = new ArrayList<>();
        for (KnowledgeSearchHit hit : result.hits()) {
            BuggyKnowledgeArea area = areaOf(hit.metadata());
            BuggyKnowledgeVisibility visibility = visibilityOf(hit.metadata(), hit.classification());
            if (!productAccessAllowed(identity, access, area, visibility)) continue;
            if (containsSecretMaterial(hit)) continue;
            BuggyKnowledgeState state = annotations.findCurrent(hit.documentId())
                    .map(BuggyKnowledgeAnnotationRevision::state)
                    .orElse(BuggyKnowledgeState.ACTIVE);
            if (state == BuggyKnowledgeState.STALE && !request.includeStale()) continue;
            if (state == BuggyKnowledgeState.DISPUTED && !request.includeDisputed()) continue;
            filtered.add(hit);
            if (filtered.size() == request.limit()) break;
        }
        return filtered.isEmpty() ? KnowledgeSearchResult.noReliableResult() : KnowledgeSearchResult.results(filtered);
    }

    public KnowledgeDocument createCorrectionDraft(
            KnowledgeDocumentId supersededDocumentId,
            BuggyKnowledgeDraft correction,
            ActorId actorId) {
        Objects.requireNonNull(supersededDocumentId, "supersededDocumentId");
        Objects.requireNonNull(correction, "correction");
        Objects.requireNonNull(actorId, "actorId");
        BuggyKnowledgeAnnotationRevision assessment = annotations.findCurrent(supersededDocumentId)
                .orElseThrow(() -> new IllegalStateException("Correction requires a STALE or DISPUTED assessment"));
        if (assessment.state() == BuggyKnowledgeState.ACTIVE) {
            throw new IllegalStateException("Correction requires a STALE or DISPUTED assessment");
        }
        if (knowledge.findDocument(supersededDocumentId).isEmpty()) {
            throw new IllegalArgumentException("Unknown Foundation Knowledge document: " + supersededDocumentId);
        }
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(correction.metadata());
        metadata.put("supersedes_document_id", supersededDocumentId.value().toString());
        metadata.put("supersedes_annotation_revision", Long.toString(assessment.revision()));
        return createDraft(new BuggyKnowledgeDraft(
                correction.area(),
                correction.topic(),
                correction.title(),
                correction.visibility(),
                correction.content(),
                correction.tags(),
                metadata,
                correction.provenance(),
                correction.sourceRef(),
                correction.sourceLabel(),
                correction.confidence(),
                correction.conflictKey(),
                correction.conflictValue()), actorId);
    }

    public BuggyKnowledgeConflictReport detectConflicts(BuggyKnowledgeDraft candidate, BuggyKnowledgeAccess access) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(access, "access");
        if (candidate.conflictKey() == null) {
            return new BuggyKnowledgeConflictReport(
                    BuggyKnowledgeConflictStatus.NO_RELIABLE_RESULT, null, null, List.of());
        }
        KnowledgeSearchResult result = search(new BuggyKnowledgeSearchRequest(
                null,
                Set.of(),
                Set.of(),
                Map.of(META_CONFLICT_KEY, candidate.conflictKey()),
                false,
                false,
                100), access);
        if (result.status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT) {
            return new BuggyKnowledgeConflictReport(
                    BuggyKnowledgeConflictStatus.NO_RELIABLE_RESULT,
                    candidate.conflictKey(),
                    candidate.conflictValue(),
                    List.of());
        }
        List<BuggyKnowledgeConflict> conflicts = new ArrayList<>();
        boolean comparableEvidence = false;
        for (KnowledgeSearchHit hit : result.hits()) {
            String currentValue = hit.metadata().get(META_CONFLICT_VALUE);
            if (currentValue == null) continue;
            comparableEvidence = true;
            if (!currentValue.equals(candidate.conflictValue())) {
                conflicts.add(new BuggyKnowledgeConflict(
                        hit.documentId(),
                        hit.revision(),
                        hit.title(),
                        currentValue,
                        confidenceOf(hit.metadata()),
                        provenanceOf(hit.source().sourceKind())));
            }
        }
        if (!conflicts.isEmpty()) {
            return new BuggyKnowledgeConflictReport(
                    BuggyKnowledgeConflictStatus.CONFLICT,
                    candidate.conflictKey(),
                    candidate.conflictValue(),
                    conflicts);
        }
        return new BuggyKnowledgeConflictReport(
                comparableEvidence ? BuggyKnowledgeConflictStatus.NO_CONFLICT : BuggyKnowledgeConflictStatus.NO_RELIABLE_RESULT,
                candidate.conflictKey(),
                candidate.conflictValue(),
                List.of());
    }

    public BuggyKnowledgeAnnotationRevision markStale(
            KnowledgeDocumentId documentId,
            String reason,
            String evidenceRef,
            BuggyKnowledgeConfidence confidence,
            ActorId actorId) {
        return annotate(documentId, BuggyKnowledgeState.STALE, reason, evidenceRef, confidence, actorId);
    }

    public BuggyKnowledgeAnnotationRevision markDisputed(
            KnowledgeDocumentId documentId,
            String reason,
            String evidenceRef,
            BuggyKnowledgeConfidence confidence,
            ActorId actorId) {
        return annotate(documentId, BuggyKnowledgeState.DISPUTED, reason, evidenceRef, confidence, actorId);
    }

    public BuggyKnowledgeAnnotationRevision resolveAnnotation(
            KnowledgeDocumentId documentId,
            String reason,
            String evidenceRef,
            BuggyKnowledgeConfidence confidence,
            ActorId actorId) {
        return annotate(documentId, BuggyKnowledgeState.ACTIVE, reason, evidenceRef, confidence, actorId);
    }

    public List<BuggyKnowledgeAnnotationRevision> annotationHistory(KnowledgeDocumentId documentId) {
        verifyKnowledgeProfile();
        return annotations.history(documentId);
    }

    private BuggyKnowledgeAnnotationRevision annotate(
            KnowledgeDocumentId documentId,
            BuggyKnowledgeState state,
            String reason,
            String evidenceRef,
            BuggyKnowledgeConfidence confidence,
            ActorId actorId) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(actorId, "actorId");
        verifyKnowledgeProfile();
        if (knowledge.findDocument(documentId).isEmpty()) {
            throw new IllegalArgumentException("Unknown Foundation Knowledge document: " + documentId);
        }
        String safeReason = requireSecretFree(reason, "reason", 1024);
        String safeEvidence = requireSecretFree(evidenceRef, "evidenceRef", 1024);
        Optional<BuggyKnowledgeAnnotationRevision> current = annotations.findCurrent(documentId);
        long expected = current.map(BuggyKnowledgeAnnotationRevision::revision).orElse(0L);
        long revision = expected + 1;
        Long previous = expected == 0 ? null : expected;
        Instant now = clock.instant();
        String sha = BuggyKnowledgeCanonicalizer.annotationSha256(
                documentId, revision, previous, state, safeReason, safeEvidence, confidence, actorId, now);
        return annotations.append(expected, new BuggyKnowledgeAnnotationRevision(
                documentId, revision, previous, state, safeReason, safeEvidence, confidence, actorId, now, sha));
    }

    private void validateDraftBoundary(BuggyKnowledgeDraft draft, BuggyIdentitySnapshot identity, ActorId actorId) {
        if (!draft.visibility().allowedAsNormalKnowledge()) {
            throw new IllegalArgumentException("SECRET is never allowed as normal Buggy Knowledge");
        }
        if (draft.area() == BuggyKnowledgeArea.DECISION_MEMORY
                && draft.provenance() != BuggyKnowledgeProvenance.OWNER_DECISION) {
            throw new IllegalArgumentException("Decision Memory requires OWNER_DECISION provenance");
        }
        if (draft.area() != BuggyKnowledgeArea.GENERAL
                || draft.provenance() == BuggyKnowledgeProvenance.OWNER_DECISION) {
            requireOwner(identity, actorId);
        }
        requireSecretFree(draft.title(), "title", 256);
        requireSecretFree(draft.content(), "content", 1_000_000);
        requireSecretFree(draft.sourceRef(), "sourceRef", 1024);
        if (draft.sourceLabel() != null) requireSecretFree(draft.sourceLabel(), "sourceLabel", 256);
        if (draft.conflictValue() != null) requireSecretFree(draft.conflictValue(), "conflictValue", 1024);
    }

    private BuggyIdentitySnapshot verifyKnowledgeProfile() {
        BuggyIdentitySnapshot identity = identityService.loadRequired();
        if (!BuggyKnowledgeProfile.PROFILE_ID.equals(identity.configuration().manifest().knowledgeProfileId())) {
            throw new IllegalStateException("Buggy identity references an unsupported Knowledge profile");
        }
        return identity;
    }

    private static void requireOwner(BuggyIdentitySnapshot identity, ActorId actorId) {
        if (!identity.ownerBinding().ownerActorId().equals(actorId)) {
            throw new SecurityException("Operation requires the NetworkCore-bound Buggy Owner");
        }
    }

    private static Set<String> aclPermissions(BuggyKnowledgeArea area, BuggyKnowledgeVisibility visibility) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (area != BuggyKnowledgeArea.GENERAL || visibility.ownerRestricted() || visibility.securitySensitive()) {
            permissions.add(BuggyKnowledgeProfile.KNOWLEDGE_READ_PERMISSION);
        }
        if (visibility.securitySensitive()) permissions.add(BuggyKnowledgeProfile.SECURITY_READ_PERMISSION);
        return Set.copyOf(permissions);
    }

    private KnowledgeAccessContext foundationAccess(
            BuggyKnowledgeAccess access,
            Set<de.legitserver.assistant.knowledge.model.KnowledgeDomain> domains) {
        return new KnowledgeAccessContext(
                access.actorId(),
                access.queryAuthorized(),
                access.effectivePermissions(),
                domains,
                BuggyKnowledgeProfile.maximumNormalClassification());
    }

    private static boolean productAccessAllowed(
            BuggyIdentitySnapshot identity,
            BuggyKnowledgeAccess access,
            BuggyKnowledgeArea area,
            BuggyKnowledgeVisibility visibility) {
        if (!visibility.allowedAsNormalKnowledge()) return false;
        if ((area == BuggyKnowledgeArea.OWNER_KNOWLEDGE || area == BuggyKnowledgeArea.DECISION_MEMORY)
                && !identity.ownerBinding().ownerActorId().equals(access.actorId())) return false;
        if (visibility.ownerRestricted() && !identity.ownerBinding().ownerActorId().equals(access.actorId())) return false;
        return !visibility.securitySensitive()
                || access.effectivePermissions().contains(BuggyKnowledgeProfile.SECURITY_READ_PERMISSION);
    }

    private boolean containsSecretMaterial(KnowledgeSearchHit hit) {
        if (hit.classification() == DataClassification.SECRET) return true;
        if (contentPolicy.containsSecretMaterial(hit.title()) || contentPolicy.containsSecretMaterial(hit.content())) return true;
        if (contentPolicy.containsSecretMaterial(hit.source().sourceRef())) return true;
        if (hit.source().sourceLabel() != null && contentPolicy.containsSecretMaterial(hit.source().sourceLabel())) return true;
        return hit.metadata().values().stream().anyMatch(contentPolicy::containsSecretMaterial);
    }

    private boolean containsSecretMaterial(KnowledgeDocument document, KnowledgeRevision revision) {
        if (document.classification() == DataClassification.SECRET) return true;
        if (contentPolicy.containsSecretMaterial(document.title()) || contentPolicy.containsSecretMaterial(revision.content())) return true;
        if (contentPolicy.containsSecretMaterial(revision.source().sourceRef())) return true;
        if (revision.source().sourceLabel() != null && contentPolicy.containsSecretMaterial(revision.source().sourceLabel())) return true;
        return revision.metadata().values().stream().anyMatch(contentPolicy::containsSecretMaterial);
    }

    private String requireSecretFree(String value, String field, int maxLength) {
        return contentPolicy.requirePersistableText(value, field, maxLength);
    }

    private static BuggyKnowledgeArea areaOf(Map<String, String> metadata) {
        String value = metadata.get(META_AREA);
        if (value == null) return BuggyKnowledgeArea.GENERAL;
        try {
            return BuggyKnowledgeArea.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            return BuggyKnowledgeArea.GENERAL;
        }
    }

    private static BuggyKnowledgeVisibility visibilityOf(
            Map<String, String> metadata, DataClassification classification) {
        String value = metadata.get(META_VISIBILITY);
        if (value != null) {
            try {
                return BuggyKnowledgeVisibility.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Fall through to conservative Foundation mapping.
            }
        }
        return switch (classification) {
            case PUBLIC -> BuggyKnowledgeVisibility.PUBLIC;
            case INTERNAL -> BuggyKnowledgeVisibility.INTERNAL;
            case CONFIDENTIAL -> BuggyKnowledgeVisibility.CONFIDENTIAL;
            case RESTRICTED -> BuggyKnowledgeVisibility.SECURITY_SENSITIVE;
            case SECRET -> BuggyKnowledgeVisibility.SECRET;
        };
    }

    private static BuggyKnowledgeConfidence confidenceOf(Map<String, String> metadata) {
        String value = metadata.get(META_CONFIDENCE);
        if (value == null) return BuggyKnowledgeConfidence.LOW;
        try {
            return BuggyKnowledgeConfidence.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return BuggyKnowledgeConfidence.LOW;
        }
    }

    private static BuggyKnowledgeProvenance provenanceOf(String sourceKind) {
        try {
            return BuggyKnowledgeProvenance.valueOf(sourceKind);
        } catch (IllegalArgumentException invalid) {
            return BuggyKnowledgeProvenance.EXTERNAL_SOURCE;
        }
    }

    private static boolean isReservedMetadata(String key) {
        return key.startsWith("buggy.") || key.startsWith("decision.");
    }
}
