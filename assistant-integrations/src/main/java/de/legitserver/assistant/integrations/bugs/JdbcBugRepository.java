package de.legitserver.assistant.integrations.bugs;

import de.legitserver.assistant.bugs.model.BugHistoryEvent;
import de.legitserver.assistant.bugs.model.BugHistoryEventType;
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
import de.legitserver.assistant.bugs.repository.BugNotFoundException;
import de.legitserver.assistant.bugs.repository.BugRepository;
import de.legitserver.assistant.bugs.repository.BugRevisionConflictException;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for the Phase-8 Bug aggregate, history, relations and linkage contracts. */
public final class JdbcBugRepository implements BugRepository {
    private static final String RECORD_COLUMNS =
            "internal_bug_id, public_bug_id, status, severity, priority, reporter_actor_id, source, "
                    + "original_report, original_report_sha256, assistant_summary, component_id, plugin_version, "
                    + "build_version, server_id, environment, classification, created_at, updated_at, revision, "
                    + "structured_metadata::text AS structured_metadata_json";
    private final TransactionRunner transactions;

    public JdbcBugRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void create(BugRecord bug, BugHistoryEvent createdEvent) {
        Objects.requireNonNull(bug, "bug");
        Objects.requireNonNull(createdEvent, "createdEvent");
        if (!bug.internalBugId().equals(createdEvent.internalBugId())) {
            throw new IllegalArgumentException("Bug and history event IDs differ");
        }
        if (createdEvent.eventType() != BugHistoryEventType.CREATED) {
            throw new IllegalArgumentException("Initial history event must be CREATED");
        }
        transactions.required(connection -> {
            insertBug(connection, bug);
            insertHistory(connection, createdEvent);
            return null;
        });
    }

    @Override
    public Optional<BugRecord> findByInternalId(InternalBugId id) {
        Objects.requireNonNull(id, "id");
        return transactions.readOnly(connection -> findByInternalId(connection, id));
    }

    @Override
    public Optional<BugRecord> findByPublicId(PublicBugId id) {
        Objects.requireNonNull(id, "id");
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + RECORD_COLUMNS + " FROM bugs.records WHERE public_bug_id=?")) {
                statement.setString(1, id.value());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapBug(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public BugRecord update(BugRecord updated, long expectedRevision, BugHistoryEvent event) {
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(event, "event");
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be >= 1");
        if (updated.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("updated revision must equal expectedRevision + 1");
        }
        if (!updated.internalBugId().equals(event.internalBugId())) {
            throw new IllegalArgumentException("Bug and history event IDs differ");
        }
        return transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE bugs.records SET status=?, severity=?, priority=?, assistant_summary=?, component_id=?, "
                            + "plugin_version=?, build_version=?, server_id=?, environment=?, updated_at=?, revision=?, "
                            + "structured_metadata=CAST(? AS jsonb) WHERE internal_bug_id=? AND revision=?")) {
                int index = 1;
                statement.setString(index++, updated.status().name());
                statement.setString(index++, updated.severity().name());
                statement.setString(index++, updated.priority().name());
                setString(statement, index++, updated.assistantSummary());
                setString(statement, index++, updated.targetMapping().componentId());
                setString(statement, index++, updated.targetMapping().pluginVersion());
                setString(statement, index++, updated.targetMapping().buildVersion());
                setString(statement, index++, updated.targetMapping().serverId());
                setString(statement, index++, updated.targetMapping().environment());
                statement.setTimestamp(index++, Timestamp.from(updated.updatedAt()));
                statement.setLong(index++, updated.revision());
                statement.setString(index++, BugJsonCodec.encode(updated.structuredMetadata()));
                statement.setObject(index++, updated.internalBugId().value());
                statement.setLong(index, expectedRevision);
                int count = statement.executeUpdate();
                if (count != 1) {
                    if (findByInternalId(connection, updated.internalBugId()).isEmpty()) {
                        throw new BugNotFoundException(updated.internalBugId());
                    }
                    throw new BugRevisionConflictException(updated.internalBugId(), expectedRevision);
                }
            }
            insertHistory(connection, event);
            return findByInternalId(connection, updated.internalBugId())
                    .orElseThrow(() -> new BugNotFoundException(updated.internalBugId()));
        });
    }

    @Override
    public List<BugHistoryEvent> history(InternalBugId id, int limit) {
        Objects.requireNonNull(id, "id");
        return transactions.readOnly(connection -> {
            List<BugHistoryEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT history_id, internal_bug_id, event_type, actor_id, before_json::text, after_json::text, "
                            + "reason, created_at FROM bugs.history WHERE internal_bug_id=? "
                            + "ORDER BY history_seq ASC LIMIT ?")) {
                statement.setObject(1, id.value());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) events.add(mapHistory(result));
                }
            }
            return List.copyOf(events);
        });
    }

    @Override
    public void addRelation(BugRelation relation, BugHistoryEvent event) {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(event, "event");
        transactions.required(connection -> {
            requireExists(connection, relation.sourceBugId());
            requireExists(connection, relation.targetBugId());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bugs.relations(source_bug_id, relation_type, target_bug_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                statement.setObject(1, relation.sourceBugId().value());
                statement.setString(2, relation.relationType().name());
                statement.setObject(3, relation.targetBugId().value());
                statement.setString(4, relation.createdBy().value());
                statement.setTimestamp(5, Timestamp.from(relation.createdAt()));
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Relation insert count != 1");
            }
            insertHistory(connection, event);
            return null;
        });
    }

    @Override
    public boolean removeRelation(
            InternalBugId sourceBugId,
            BugRelationType relationType,
            InternalBugId targetBugId,
            BugHistoryEvent event) {
        Objects.requireNonNull(sourceBugId, "sourceBugId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(targetBugId, "targetBugId");
        Objects.requireNonNull(event, "event");
        return transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM bugs.relations WHERE source_bug_id=? AND relation_type=? AND target_bug_id=?")) {
                statement.setObject(1, sourceBugId.value());
                statement.setString(2, relationType.name());
                statement.setObject(3, targetBugId.value());
                int count = statement.executeUpdate();
                if (count == 0) return false;
                if (count != 1) throw new IllegalStateException("Relation delete count != 1");
            }
            insertHistory(connection, event);
            return true;
        });
    }

    @Override
    public List<BugRelation> relations(InternalBugId bugId, int limit) {
        Objects.requireNonNull(bugId, "bugId");
        return transactions.readOnly(connection -> {
            List<BugRelation> relations = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT source_bug_id, relation_type, target_bug_id, created_by, created_at FROM bugs.relations "
                            + "WHERE source_bug_id=? OR target_bug_id=? ORDER BY created_at ASC LIMIT ?")) {
                statement.setObject(1, bugId.value());
                statement.setObject(2, bugId.value());
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        relations.add(new BugRelation(
                                new InternalBugId(result.getObject("source_bug_id", UUID.class)),
                                BugRelationType.valueOf(result.getString("relation_type")),
                                new InternalBugId(result.getObject("target_bug_id", UUID.class)),
                                new ActorId(result.getString("created_by")),
                                result.getTimestamp("created_at").toInstant()));
                    }
                }
            }
            return List.copyOf(relations);
        });
    }

    @Override
    public void linkIncident(BugIncidentLink link, BugHistoryEvent event) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(event, "event");
        transactions.required(connection -> {
            requireExists(connection, link.internalBugId());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bugs.incident_links(internal_bug_id, internal_incident_id, public_error_id, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                statement.setObject(1, link.internalBugId().value());
                statement.setString(2, link.internalIncidentId());
                setString(statement, 3, link.publicErrorId());
                statement.setString(4, link.createdBy().value());
                statement.setTimestamp(5, Timestamp.from(link.createdAt()));
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Incident link insert count != 1");
            }
            insertHistory(connection, event);
            return null;
        });
    }

    @Override
    public List<BugIncidentLink> incidentLinks(InternalBugId bugId, int limit) {
        Objects.requireNonNull(bugId, "bugId");
        return transactions.readOnly(connection -> {
            List<BugIncidentLink> links = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT internal_bug_id, internal_incident_id, public_error_id, created_by, created_at "
                            + "FROM bugs.incident_links WHERE internal_bug_id=? ORDER BY created_at ASC LIMIT ?")) {
                statement.setObject(1, bugId.value());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        links.add(new BugIncidentLink(
                                new InternalBugId(result.getObject("internal_bug_id", UUID.class)),
                                result.getString("internal_incident_id"),
                                result.getString("public_error_id"),
                                new ActorId(result.getString("created_by")),
                                result.getTimestamp("created_at").toInstant()));
                    }
                }
            }
            return List.copyOf(links);
        });
    }

    @Override
    public List<BugRecord> findByTargetMapping(BugTargetMapping mapping, int limit) {
        Objects.requireNonNull(mapping, "mapping");
        if (mapping.isEmpty()) throw new IllegalArgumentException("At least one mapping field is required");
        return transactions.readOnly(connection -> {
            StringBuilder sql = new StringBuilder("SELECT ").append(RECORD_COLUMNS).append(" FROM bugs.records WHERE 1=1");
            List<String> values = new ArrayList<>();
            addFilter(sql, values, "component_id", mapping.componentId());
            addFilter(sql, values, "plugin_version", mapping.pluginVersion());
            addFilter(sql, values, "build_version", mapping.buildVersion());
            addFilter(sql, values, "server_id", mapping.serverId());
            addFilter(sql, values, "environment", mapping.environment());
            sql.append(" ORDER BY updated_at DESC LIMIT ?");
            List<BugRecord> bugs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                for (String value : values) statement.setString(index++, value);
                statement.setInt(index, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) bugs.add(mapBug(result));
                }
            }
            return List.copyOf(bugs);
        });
    }

    private static void addFilter(StringBuilder sql, List<String> values, String column, String value) {
        if (value == null) return;
        sql.append(" AND ").append(column).append("=?");
        values.add(value);
    }

    private static void insertBug(Connection connection, BugRecord bug) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bugs.records(internal_bug_id, public_bug_id, status, severity, priority, reporter_actor_id, "
                        + "source, original_report, original_report_sha256, assistant_summary, component_id, plugin_version, "
                        + "build_version, server_id, environment, classification, created_at, updated_at, revision, structured_metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))")) {
            int index = 1;
            statement.setObject(index++, bug.internalBugId().value());
            statement.setString(index++, bug.publicBugId().value());
            statement.setString(index++, bug.status().name());
            statement.setString(index++, bug.severity().name());
            statement.setString(index++, bug.priority().name());
            statement.setString(index++, bug.reporterActorId().value());
            statement.setString(index++, bug.source().value());
            statement.setString(index++, bug.originalReport());
            statement.setString(index++, bug.originalReportSha256());
            setString(statement, index++, bug.assistantSummary());
            setString(statement, index++, bug.targetMapping().componentId());
            setString(statement, index++, bug.targetMapping().pluginVersion());
            setString(statement, index++, bug.targetMapping().buildVersion());
            setString(statement, index++, bug.targetMapping().serverId());
            setString(statement, index++, bug.targetMapping().environment());
            statement.setString(index++, bug.classification().name());
            statement.setTimestamp(index++, Timestamp.from(bug.createdAt()));
            statement.setTimestamp(index++, Timestamp.from(bug.updatedAt()));
            statement.setLong(index++, bug.revision());
            statement.setString(index, BugJsonCodec.encode(bug.structuredMetadata()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Bug insert count != 1");
        }
    }

    private static void insertHistory(Connection connection, BugHistoryEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bugs.history(history_id, internal_bug_id, event_type, actor_id, before_json, after_json, reason, created_at) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)")) {
            statement.setObject(1, event.historyId());
            statement.setObject(2, event.internalBugId().value());
            statement.setString(3, event.eventType().name());
            statement.setString(4, event.actorId().value());
            statement.setString(5, BugJsonCodec.encode(event.before()));
            statement.setString(6, BugJsonCodec.encode(event.after()));
            setString(statement, 7, event.reason());
            statement.setTimestamp(8, Timestamp.from(event.createdAt()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("History insert count != 1");
        }
    }

    private static Optional<BugRecord> findByInternalId(Connection connection, InternalBugId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + RECORD_COLUMNS + " FROM bugs.records WHERE internal_bug_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapBug(result)) : Optional.empty();
            }
        }
    }

    private static void requireExists(Connection connection, InternalBugId id) throws SQLException {
        if (findByInternalId(connection, id).isEmpty()) throw new BugNotFoundException(id);
    }

    private static BugRecord mapBug(ResultSet result) throws SQLException {
        return new BugRecord(
                new InternalBugId(result.getObject("internal_bug_id", UUID.class)),
                PublicBugId.of(result.getString("public_bug_id")),
                BugStatus.valueOf(result.getString("status")),
                BugSeverity.valueOf(result.getString("severity")),
                BugPriority.valueOf(result.getString("priority")),
                new ActorId(result.getString("reporter_actor_id")),
                BugSource.of(result.getString("source")),
                result.getString("original_report"),
                result.getString("original_report_sha256"),
                result.getString("assistant_summary"),
                new BugTargetMapping(
                        result.getString("component_id"),
                        result.getString("plugin_version"),
                        result.getString("build_version"),
                        result.getString("server_id"),
                        result.getString("environment")),
                DataClassification.valueOf(result.getString("classification")),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("revision"),
                BugJsonCodec.decode(result.getString("structured_metadata_json")));
    }

    private static BugHistoryEvent mapHistory(ResultSet result) throws SQLException {
        return new BugHistoryEvent(
                result.getObject("history_id", UUID.class),
                new InternalBugId(result.getObject("internal_bug_id", UUID.class)),
                BugHistoryEventType.valueOf(result.getString("event_type")),
                new ActorId(result.getString("actor_id")),
                BugJsonCodec.decode(result.getString("before_json")),
                BugJsonCodec.decode(result.getString("after_json")),
                result.getString("reason"),
                result.getTimestamp("created_at").toInstant());
    }

    private static void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value);
    }
}
