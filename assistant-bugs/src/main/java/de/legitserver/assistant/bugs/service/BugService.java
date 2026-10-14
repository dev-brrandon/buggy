package de.legitserver.assistant.bugs.service;

import de.legitserver.assistant.bugs.model.BugDraft;
import de.legitserver.assistant.bugs.model.BugHistoryEvent;
import de.legitserver.assistant.bugs.model.BugHistoryEventType;
import de.legitserver.assistant.bugs.model.BugIncidentLink;
import de.legitserver.assistant.bugs.model.BugPriority;
import de.legitserver.assistant.bugs.model.BugRecord;
import de.legitserver.assistant.bugs.model.BugRelation;
import de.legitserver.assistant.bugs.model.BugRelationType;
import de.legitserver.assistant.bugs.model.BugSeverity;
import de.legitserver.assistant.bugs.model.BugStatus;
import de.legitserver.assistant.bugs.model.BugTargetMapping;
import de.legitserver.assistant.bugs.model.InternalBugId;
import de.legitserver.assistant.bugs.model.PublicBugId;
import de.legitserver.assistant.bugs.policy.BugLimits;
import de.legitserver.assistant.bugs.repository.BugNotFoundException;
import de.legitserver.assistant.bugs.repository.BugRepository;
import de.legitserver.assistant.bugs.repository.BugRevisionConflictException;
import de.legitserver.assistant.bugs.validation.BugValidation;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Transaction-agnostic application service for the central Bug Domain. */
public final class BugService {
    private final BugRepository repository;
    private final Clock clock;

    public BugService(BugRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BugRecord create(BugDraft draft, ActorId actorId) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(actorId, "actorId");
        Instant now = clock.instant();
        InternalBugId id = InternalBugId.random();
        BugRecord bug = new BugRecord(
                id,
                draft.publicBugId(),
                BugStatus.OPEN,
                draft.severity(),
                draft.priority(),
                draft.reporterActorId(),
                draft.source(),
                draft.originalReport(),
                BugValidation.sha256Of(draft.originalReport()),
                draft.assistantSummary(),
                draft.targetMapping(),
                draft.classification(),
                now,
                now,
                1,
                draft.structuredMetadata());
        BugHistoryEvent event = BugHistoryEvent.create(
                id,
                BugHistoryEventType.CREATED,
                actorId,
                Map.of(),
                creationSnapshot(bug),
                null,
                now);
        repository.create(bug, event);
        return bug;
    }

    public BugRecord changeStatus(
            InternalBugId id, long expectedRevision, BugStatus status, ActorId actorId, String reason) {
        BugRecord current = requireBug(id);
        Objects.requireNonNull(status, "status");
        if (current.status() == status) throw new IllegalArgumentException("Bug status is unchanged");
        BugRecord updated = copy(current, status, current.severity(), current.priority(), current.assistantSummary(), current.targetMapping(), current.structuredMetadata());
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.STATUS_CHANGED, actorId,
                Map.of("status", current.status().name()), Map.of("status", status.name()), reason);
    }

    public BugRecord changeSeverity(
            InternalBugId id, long expectedRevision, BugSeverity severity, ActorId actorId, String reason) {
        BugRecord current = requireBug(id);
        Objects.requireNonNull(severity, "severity");
        if (current.severity() == severity) throw new IllegalArgumentException("Bug severity is unchanged");
        BugRecord updated = copy(current, current.status(), severity, current.priority(), current.assistantSummary(), current.targetMapping(), current.structuredMetadata());
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.SEVERITY_CHANGED, actorId,
                Map.of("severity", current.severity().name()), Map.of("severity", severity.name()), reason);
    }

    public BugRecord changePriority(
            InternalBugId id, long expectedRevision, BugPriority priority, ActorId actorId, String reason) {
        BugRecord current = requireBug(id);
        Objects.requireNonNull(priority, "priority");
        if (current.priority() == priority) throw new IllegalArgumentException("Bug priority is unchanged");
        BugRecord updated = copy(current, current.status(), current.severity(), priority, current.assistantSummary(), current.targetMapping(), current.structuredMetadata());
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.PRIORITY_CHANGED, actorId,
                Map.of("priority", current.priority().name()), Map.of("priority", priority.name()), reason);
    }

    public BugRecord updateSummary(
            InternalBugId id, long expectedRevision, String summary, ActorId actorId, String reason) {
        BugRecord current = requireBug(id);
        String normalized = BugValidation.assistantSummary(summary);
        if (Objects.equals(current.assistantSummary(), normalized)) throw new IllegalArgumentException("Bug summary is unchanged");
        BugRecord updated = copy(current, current.status(), current.severity(), current.priority(), normalized, current.targetMapping(), current.structuredMetadata());
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.SUMMARY_CHANGED, actorId,
                nullableMap("assistantSummary", current.assistantSummary()), nullableMap("assistantSummary", normalized), reason);
    }

    public BugRecord updateTargetMapping(
            InternalBugId id,
            long expectedRevision,
            BugTargetMapping mapping,
            ActorId actorId,
            String reason) {
        BugRecord current = requireBug(id);
        Objects.requireNonNull(mapping, "mapping");
        if (current.targetMapping().equals(mapping)) throw new IllegalArgumentException("Bug target mapping is unchanged");
        BugRecord updated = copy(current, current.status(), current.severity(), current.priority(), current.assistantSummary(), mapping, current.structuredMetadata());
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.TARGET_MAPPING_CHANGED, actorId,
                mappingSnapshot(current.targetMapping()), mappingSnapshot(mapping), reason);
    }

    public BugRecord updateMetadata(
            InternalBugId id,
            long expectedRevision,
            Map<String, String> metadata,
            ActorId actorId,
            String reason) {
        BugRecord current = requireBug(id);
        Map<String, String> normalized = BugValidation.metadata(metadata);
        if (current.structuredMetadata().equals(normalized)) throw new IllegalArgumentException("Bug metadata is unchanged");
        BugRecord updated = copy(current, current.status(), current.severity(), current.priority(), current.assistantSummary(), current.targetMapping(), normalized);
        return persistChange(current, updated, expectedRevision, BugHistoryEventType.METADATA_CHANGED, actorId,
                current.structuredMetadata(), normalized, reason);
    }

    public BugRelation addRelation(
            InternalBugId sourceId,
            BugRelationType type,
            InternalBugId targetId,
            ActorId actorId,
            String reason) {
        requireBug(sourceId);
        requireBug(targetId);
        Instant now = clock.instant();
        BugRelation relation = new BugRelation(sourceId, type, targetId, actorId, now);
        BugHistoryEvent event = BugHistoryEvent.create(
                sourceId,
                BugHistoryEventType.RELATION_ADDED,
                actorId,
                Map.of(),
                relationSnapshot(type, targetId),
                reason,
                now);
        repository.addRelation(relation, event);
        return relation;
    }

    public boolean removeRelation(
            InternalBugId sourceId,
            BugRelationType type,
            InternalBugId targetId,
            ActorId actorId,
            String reason) {
        requireBug(sourceId);
        Instant now = clock.instant();
        BugHistoryEvent event = BugHistoryEvent.create(
                sourceId,
                BugHistoryEventType.RELATION_REMOVED,
                actorId,
                relationSnapshot(type, targetId),
                Map.of(),
                reason,
                now);
        return repository.removeRelation(sourceId, type, targetId, event);
    }

    public BugIncidentLink linkIncident(
            InternalBugId bugId,
            String internalIncidentId,
            String publicErrorId,
            ActorId actorId,
            String reason) {
        requireBug(bugId);
        Instant now = clock.instant();
        BugIncidentLink link = new BugIncidentLink(bugId, internalIncidentId, publicErrorId, actorId, now);
        Map<String, String> after = new LinkedHashMap<>();
        after.put("internalIncidentId", link.internalIncidentId());
        if (link.publicErrorId() != null) after.put("publicErrorId", link.publicErrorId());
        BugHistoryEvent event = BugHistoryEvent.create(
                bugId, BugHistoryEventType.INCIDENT_LINKED, actorId, Map.of(), after, reason, now);
        repository.linkIncident(link, event);
        return link;
    }

    public Optional<BugRecord> findByInternalId(InternalBugId id) {
        return repository.findByInternalId(id);
    }

    public Optional<BugRecord> findByPublicId(PublicBugId id) {
        return repository.findByPublicId(id);
    }

    public List<BugHistoryEvent> history(InternalBugId id, int limit) {
        return repository.history(id, requireLimit(limit));
    }

    public List<BugRelation> relations(InternalBugId id, int limit) {
        return repository.relations(id, requireLimit(limit));
    }

    public List<BugIncidentLink> incidentLinks(InternalBugId id, int limit) {
        return repository.incidentLinks(id, requireLimit(limit));
    }

    public List<BugRecord> findByTargetMapping(BugTargetMapping mapping, int limit) {
        Objects.requireNonNull(mapping, "mapping");
        if (mapping.isEmpty()) throw new IllegalArgumentException("At least one target mapping field is required");
        return repository.findByTargetMapping(mapping, requireLimit(limit));
    }

    private BugRecord requireBug(InternalBugId id) {
        Objects.requireNonNull(id, "id");
        return repository.findByInternalId(id).orElseThrow(() -> new BugNotFoundException(id));
    }

    private BugRecord persistChange(
            BugRecord current,
            BugRecord updated,
            long expectedRevision,
            BugHistoryEventType eventType,
            ActorId actorId,
            Map<String, String> before,
            Map<String, String> after,
            String reason) {
        if (current.revision() != expectedRevision) {
            throw new BugRevisionConflictException(current.internalBugId(), expectedRevision);
        }
        BugHistoryEvent event = BugHistoryEvent.create(
                current.internalBugId(), eventType, actorId, before, after, reason, updated.updatedAt());
        return repository.update(updated, expectedRevision, event);
    }

    private BugRecord copy(
            BugRecord current,
            BugStatus status,
            BugSeverity severity,
            BugPriority priority,
            String summary,
            BugTargetMapping mapping,
            Map<String, String> metadata) {
        Instant now = clock.instant();
        return new BugRecord(
                current.internalBugId(),
                current.publicBugId(),
                status,
                severity,
                priority,
                current.reporterActorId(),
                current.source(),
                current.originalReport(),
                current.originalReportSha256(),
                summary,
                mapping,
                current.classification(),
                current.createdAt(),
                now,
                current.revision() + 1,
                metadata);
    }

    private static Map<String, String> creationSnapshot(BugRecord bug) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("status", bug.status().name());
        values.put("severity", bug.severity().name());
        values.put("priority", bug.priority().name());
        values.put("originalReportSha256", bug.originalReportSha256());
        values.putAll(mappingSnapshot(bug.targetMapping()));
        return values;
    }

    private static Map<String, String> mappingSnapshot(BugTargetMapping mapping) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (mapping.componentId() != null) values.put("componentId", mapping.componentId());
        if (mapping.pluginVersion() != null) values.put("pluginVersion", mapping.pluginVersion());
        if (mapping.buildVersion() != null) values.put("buildVersion", mapping.buildVersion());
        if (mapping.serverId() != null) values.put("serverId", mapping.serverId());
        if (mapping.environment() != null) values.put("environment", mapping.environment());
        return values;
    }

    private static Map<String, String> relationSnapshot(BugRelationType type, InternalBugId targetId) {
        return Map.of("relationType", type.name(), "targetBugId", targetId.value().toString());
    }

    private static Map<String, String> nullableMap(String key, String value) {
        return value == null ? Map.of() : Map.of(key, value);
    }

    private static int requireLimit(int limit) {
        if (limit < 1 || limit > BugLimits.MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + BugLimits.MAX_LIST_LIMIT);
        }
        return limit;
    }
}
