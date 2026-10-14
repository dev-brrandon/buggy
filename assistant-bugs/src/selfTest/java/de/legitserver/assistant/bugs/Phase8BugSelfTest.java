package de.legitserver.assistant.bugs;

import de.legitserver.assistant.bugs.model.BugDraft;
import de.legitserver.assistant.bugs.model.BugHistoryEvent;
import de.legitserver.assistant.bugs.model.BugIncidentLink;
import de.legitserver.assistant.bugs.model.BugPriority;
import de.legitserver.assistant.bugs.model.BugRecord;
import de.legitserver.assistant.bugs.model.BugRelation;
import de.legitserver.assistant.bugs.model.BugRelationType;
import de.legitserver.assistant.bugs.model.BugSeverity;
import de.legitserver.assistant.bugs.model.BugSource;
import de.legitserver.assistant.bugs.model.BugStatus;
import de.legitserver.assistant.bugs.model.BugTargetMapping;
import de.legitserver.assistant.bugs.model.InternalBugId;
import de.legitserver.assistant.bugs.model.PublicBugId;
import de.legitserver.assistant.bugs.repository.BugRepository;
import de.legitserver.assistant.bugs.repository.BugRevisionConflictException;
import de.legitserver.assistant.bugs.service.BugService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Framework-free Phase-8 Bug Foundation verification. */
public final class Phase8BugSelfTest {
    private static final ActorId ACTOR = new ActorId("staff.test");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T08:00:00Z"), ZoneOffset.UTC);

    private Phase8BugSelfTest() {}

    public static void main(String[] args) {
        MemoryRepository repository = new MemoryRepository();
        BugService service = new BugService(repository, CLOCK);

        BugRecord bug = service.create(draft("TEST-0001", "Original immutable reporter text"), ACTOR);
        require(bug.status() == BugStatus.OPEN, "new Bug is OPEN");
        require(bug.revision() == 1, "new Bug revision");
        String originalText = bug.originalReport();
        String originalHash = bug.originalReportSha256();

        bug = service.changeStatus(bug.internalBugId(), 1, BugStatus.TRIAGED, ACTOR, "triaged");
        bug = service.changeSeverity(bug.internalBugId(), 2, BugSeverity.CRITICAL, ACTOR, "impact confirmed");
        bug = service.changePriority(bug.internalBugId(), 3, BugPriority.P0, ACTOR, "release blocker");
        bug = service.updateSummary(bug.internalBugId(), 4, "Reproduced on test network", ACTOR, "analysis");
        bug = service.updateTargetMapping(
                bug.internalBugId(),
                5,
                new BugTargetMapping("system-paper", "1.1.0", "build-50", "survival", "production"),
                ACTOR,
                "mapped");
        bug = service.updateMetadata(bug.internalBugId(), 6, Map.of("reproducible", "true"), ACTOR, "metadata");

        require(bug.revision() == 7, "revision increments for every Bug head update");
        require(bug.originalReport().equals(originalText), "original report stayed immutable");
        require(bug.originalReportSha256().equals(originalHash), "original report hash stayed immutable");
        require(service.history(bug.internalBugId(), 50).size() == 7, "complete head mutation history");

        BugRecord target = service.create(draft("TEST-0002", "Related bug"), ACTOR);
        service.addRelation(bug.internalBugId(), BugRelationType.BLOCKS, target.internalBugId(), ACTOR, "dependency");
        require(service.relations(bug.internalBugId(), 20).size() == 1, "relation stored");
        require(service.removeRelation(
                        bug.internalBugId(), BugRelationType.BLOCKS, target.internalBugId(), ACTOR, "resolved")
                , "relation removed");
        require(service.relations(bug.internalBugId(), 20).isEmpty(), "relation removal visible");

        service.linkIncident(bug.internalBugId(), "incident-abc", "ERR-1234", ACTOR, "technical failure");
        require(service.incidentLinks(bug.internalBugId(), 20).size() == 1, "incident linkage");
        require(service.history(bug.internalBugId(), 50).size() == 10, "relation and incident events are in history");

        List<BugRecord> mapped = service.findByTargetMapping(
                new BugTargetMapping("system-paper", "1.1.0", "build-50", null, null), 20);
        require(mapped.size() == 1 && mapped.get(0).internalBugId().equals(bug.internalBugId()), "version mapping lookup");

        boolean staleRejected = false;
        try {
            service.changePriority(bug.internalBugId(), 6, BugPriority.P1, ACTOR, "stale");
        } catch (BugRevisionConflictException expected) {
            staleRejected = true;
        }
        require(staleRejected, "stale update rejected");

        boolean selfRelationRejected = false;
        try {
            service.addRelation(bug.internalBugId(), BugRelationType.RELATED_TO, bug.internalBugId(), ACTOR, null);
        } catch (IllegalArgumentException expected) {
            selfRelationRejected = true;
        }
        require(selfRelationRejected, "self relation rejected");

        System.out.println("PHASE8_BUG_SELF_TEST: PASS");
    }

    private static BugDraft draft(String publicId, String report) {
        return new BugDraft(
                PublicBugId.of(publicId),
                BugSeverity.HIGH,
                BugPriority.P2,
                ACTOR,
                BugSource.of("ASSISTANT"),
                report,
                null,
                BugTargetMapping.empty(),
                DataClassification.INTERNAL,
                Map.of("channel", "selftest"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static final class MemoryRepository implements BugRepository {
        private final Map<InternalBugId, BugRecord> bugs = new LinkedHashMap<>();
        private final List<BugHistoryEvent> events = new ArrayList<>();
        private final List<BugRelation> relations = new ArrayList<>();
        private final List<BugIncidentLink> links = new ArrayList<>();

        @Override
        public void create(BugRecord bug, BugHistoryEvent createdEvent) {
            if (bugs.values().stream().anyMatch(current -> current.publicBugId().equals(bug.publicBugId()))) {
                throw new IllegalStateException("duplicate publicBugId");
            }
            bugs.put(bug.internalBugId(), bug);
            events.add(createdEvent);
        }

        @Override
        public Optional<BugRecord> findByInternalId(InternalBugId id) {
            return Optional.ofNullable(bugs.get(id));
        }

        @Override
        public Optional<BugRecord> findByPublicId(PublicBugId id) {
            return bugs.values().stream().filter(bug -> bug.publicBugId().equals(id)).findFirst();
        }

        @Override
        public BugRecord update(BugRecord updated, long expectedRevision, BugHistoryEvent event) {
            BugRecord current = bugs.get(updated.internalBugId());
            if (current == null || current.revision() != expectedRevision) {
                throw new BugRevisionConflictException(updated.internalBugId(), expectedRevision);
            }
            if (updated.revision() != expectedRevision + 1) throw new IllegalArgumentException("bad revision");
            bugs.put(updated.internalBugId(), updated);
            events.add(event);
            return updated;
        }

        @Override
        public List<BugHistoryEvent> history(InternalBugId id, int limit) {
            return events.stream().filter(event -> event.internalBugId().equals(id)).limit(limit).toList();
        }

        @Override
        public void addRelation(BugRelation relation, BugHistoryEvent event) {
            relations.add(relation);
            events.add(event);
        }

        @Override
        public boolean removeRelation(
                InternalBugId sourceBugId,
                BugRelationType relationType,
                InternalBugId targetBugId,
                BugHistoryEvent event) {
            boolean removed = relations.removeIf(relation -> relation.sourceBugId().equals(sourceBugId)
                    && relation.relationType() == relationType
                    && relation.targetBugId().equals(targetBugId));
            if (removed) events.add(event);
            return removed;
        }

        @Override
        public List<BugRelation> relations(InternalBugId bugId, int limit) {
            return relations.stream()
                    .filter(relation -> relation.sourceBugId().equals(bugId) || relation.targetBugId().equals(bugId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void linkIncident(BugIncidentLink link, BugHistoryEvent event) {
            links.add(link);
            events.add(event);
        }

        @Override
        public List<BugIncidentLink> incidentLinks(InternalBugId bugId, int limit) {
            return links.stream().filter(link -> link.internalBugId().equals(bugId)).limit(limit).toList();
        }

        @Override
        public List<BugRecord> findByTargetMapping(BugTargetMapping mapping, int limit) {
            return bugs.values().stream().filter(bug -> matches(bug.targetMapping(), mapping)).limit(limit).toList();
        }

        private static boolean matches(BugTargetMapping actual, BugTargetMapping filter) {
            return (filter.componentId() == null || filter.componentId().equals(actual.componentId()))
                    && (filter.pluginVersion() == null || filter.pluginVersion().equals(actual.pluginVersion()))
                    && (filter.buildVersion() == null || filter.buildVersion().equals(actual.buildVersion()))
                    && (filter.serverId() == null || filter.serverId().equals(actual.serverId()))
                    && (filter.environment() == null || filter.environment().equals(actual.environment()));
        }
    }
}
