package de.legitserver.assistant.bugs.repository;

import de.legitserver.assistant.bugs.model.BugHistoryEvent;
import de.legitserver.assistant.bugs.model.BugIncidentLink;
import de.legitserver.assistant.bugs.model.BugRecord;
import de.legitserver.assistant.bugs.model.BugRelation;
import de.legitserver.assistant.bugs.model.BugRelationType;
import de.legitserver.assistant.bugs.model.BugTargetMapping;
import de.legitserver.assistant.bugs.model.InternalBugId;
import de.legitserver.assistant.bugs.model.PublicBugId;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for Bug heads, append-only history, relations and linkage metadata. */
public interface BugRepository {
    void create(BugRecord bug, BugHistoryEvent createdEvent);

    Optional<BugRecord> findByInternalId(InternalBugId id);

    Optional<BugRecord> findByPublicId(PublicBugId id);

    BugRecord update(BugRecord updated, long expectedRevision, BugHistoryEvent event);

    List<BugHistoryEvent> history(InternalBugId id, int limit);

    void addRelation(BugRelation relation, BugHistoryEvent event);

    boolean removeRelation(
            InternalBugId sourceBugId,
            BugRelationType relationType,
            InternalBugId targetBugId,
            BugHistoryEvent event);

    List<BugRelation> relations(InternalBugId bugId, int limit);

    void linkIncident(BugIncidentLink link, BugHistoryEvent event);

    List<BugIncidentLink> incidentLinks(InternalBugId bugId, int limit);

    List<BugRecord> findByTargetMapping(BugTargetMapping mapping, int limit);
}
