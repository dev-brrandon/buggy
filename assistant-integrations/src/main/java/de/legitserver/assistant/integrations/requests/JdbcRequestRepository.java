package de.legitserver.assistant.integrations.requests;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import de.legitserver.assistant.requests.model.ApprovalDecision;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestHistoryEventType;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.repository.DuplicateApprovalException;
import de.legitserver.assistant.requests.repository.RequestNotFoundException;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.repository.RequestRevisionConflictException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for the Phase-9 Request / Approval aggregate. */
public final class JdbcRequestRepository implements RequestRepository {
    private static final String RECORD_COLUMNS =
            "request_id, request_type, requester_actor_id, state, risk_level, required_approvals, "
                    + "active_payload_revision, active_payload_sha256, approved_payload_revision, "
                    + "approved_payload_sha256, expires_at, classification, created_at, updated_at, revision";

    private final TransactionRunner transactions;

    public JdbcRequestRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdEvent, "createdEvent");
        if (!request.requestId().equals(payload.requestId())
                || !request.requestId().equals(createdEvent.requestId())) {
            throw new IllegalArgumentException("Request aggregate IDs differ");
        }
        transactions.required(connection -> {
            insertRequest(connection, request);
            insertPayload(connection, payload);
            insertHistory(connection, createdEvent);
            return null;
        });
    }

    @Override
    public Optional<RequestRecord> findById(ApprovalRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return transactions.readOnly(connection -> findById(connection, requestId));
    }

    @Override
    public Optional<RequestPayload> activePayload(ApprovalRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT p.request_id, p.payload_revision, p.content_type, p.payload_body, p.payload_sha256, "
                            + "p.revised_by, p.created_at FROM requests.payload_revisions p "
                            + "JOIN requests.records r ON r.request_id=p.request_id "
                            + "AND r.active_payload_revision=p.payload_revision WHERE p.request_id=?")) {
                statement.setObject(1, requestId.value());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapPayload(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
        Objects.requireNonNull(requestId, "requestId");
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM requests.approvals WHERE request_id=? AND payload_revision=? "
                            + "AND payload_sha256=? AND decision='APPROVE'")) {
                statement.setObject(1, requestId.value());
                statement.setLong(2, payloadRevision);
                statement.setString(3, payloadSha256);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Approval count result missing");
                    return result.getLong(1);
                }
            }
        });
    }

    @Override
    public RequestRecord revisePayload(
            RequestRecord updated,
            long expectedRevision,
            RequestPayload payload,
            RequestHistoryEvent event) {
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(event, "event");
        return transactions.required(connection -> {
            updateHead(connection, updated, expectedRevision);
            insertPayload(connection, payload);
            insertHistory(connection, event);
            return requireById(connection, updated.requestId());
        });
    }

    @Override
    public RequestRecord recordDecision(
            RequestRecord updated,
            long expectedRevision,
            RequestApproval approval,
            RequestHistoryEvent event) {
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(approval, "approval");
        Objects.requireNonNull(event, "event");
        return transactions.required(connection -> {
            updateHead(connection, updated, expectedRevision);
            insertApproval(connection, approval);
            insertHistory(connection, event);
            return requireById(connection, updated.requestId());
        });
    }

    @Override
    public RequestRecord transition(
            RequestRecord updated, long expectedRevision, RequestHistoryEvent event) {
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(event, "event");
        return transactions.required(connection -> {
            updateHead(connection, updated, expectedRevision);
            insertHistory(connection, event);
            return requireById(connection, updated.requestId());
        });
    }

    @Override
    public List<RequestApproval> approvals(ApprovalRequestId requestId, int limit) {
        Objects.requireNonNull(requestId, "requestId");
        return transactions.readOnly(connection -> {
            List<RequestApproval> approvals = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT approval_id, request_id, payload_revision, payload_sha256, approver_actor_id, decision, "
                            + "reason, created_at FROM requests.approvals WHERE request_id=? ORDER BY created_at, approval_id LIMIT ?")) {
                statement.setObject(1, requestId.value());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) approvals.add(mapApproval(result));
                }
            }
            return List.copyOf(approvals);
        });
    }

    @Override
    public List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
        Objects.requireNonNull(requestId, "requestId");
        return transactions.readOnly(connection -> {
            List<RequestHistoryEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT history_id, request_id, event_type, actor_id, payload_revision, payload_sha256, "
                            + "before_state, after_state, details::text AS details_json, created_at "
                            + "FROM requests.history WHERE request_id=? ORDER BY history_seq ASC LIMIT ?")) {
                statement.setObject(1, requestId.value());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) events.add(mapHistory(result));
                }
            }
            return List.copyOf(events);
        });
    }

    private static void insertRequest(Connection connection, RequestRecord request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO requests.records(request_id,request_type,requester_actor_id,state,risk_level,"
                        + "required_approvals,active_payload_revision,active_payload_sha256,approved_payload_revision,"
                        + "approved_payload_sha256,expires_at,classification,created_at,updated_at,revision) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            statement.setObject(i++, request.requestId().value());
            statement.setString(i++, request.requestType());
            statement.setString(i++, request.requesterActorId().value());
            statement.setString(i++, request.state().name());
            statement.setString(i++, request.riskLevel().name());
            statement.setInt(i++, request.requiredApprovals());
            statement.setLong(i++, request.activePayloadRevision());
            statement.setString(i++, request.activePayloadSha256());
            setLong(statement, i++, request.approvedPayloadRevision());
            setString(statement, i++, request.approvedPayloadSha256());
            setTimestamp(statement, i++, request.expiresAt());
            statement.setString(i++, request.classification().name());
            statement.setTimestamp(i++, Timestamp.from(request.createdAt()));
            statement.setTimestamp(i++, Timestamp.from(request.updatedAt()));
            statement.setLong(i, request.revision());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Request insert count != 1");
        }
    }

    private static void insertPayload(Connection connection, RequestPayload payload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO requests.payload_revisions(request_id,payload_revision,content_type,payload_body,"
                        + "payload_sha256,revised_by,created_at) VALUES (?,?,?,?,?,?,?)")) {
            statement.setObject(1, payload.requestId().value());
            statement.setLong(2, payload.payloadRevision());
            statement.setString(3, payload.contentType());
            statement.setString(4, payload.body());
            statement.setString(5, payload.sha256());
            statement.setString(6, payload.revisedBy().value());
            statement.setTimestamp(7, Timestamp.from(payload.createdAt()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Payload insert count != 1");
        }
    }

    private static void insertApproval(Connection connection, RequestApproval approval) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO requests.approvals(approval_id,request_id,payload_revision,payload_sha256,"
                        + "approver_actor_id,decision,reason,created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            statement.setObject(1, approval.approvalId());
            statement.setObject(2, approval.requestId().value());
            statement.setLong(3, approval.payloadRevision());
            statement.setString(4, approval.payloadSha256());
            statement.setString(5, approval.approverActorId().value());
            statement.setString(6, approval.decision().name());
            setString(statement, 7, approval.reason());
            statement.setTimestamp(8, Timestamp.from(approval.createdAt()));
            try {
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Approval insert count != 1");
            } catch (SQLException failure) {
                if ("23505".equals(failure.getSQLState())) {
                    throw new DuplicateApprovalException(
                            approval.requestId(), approval.payloadRevision(), approval.approverActorId());
                }
                throw failure;
            }
        }
    }

    private static void insertHistory(Connection connection, RequestHistoryEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO requests.history(history_id,request_id,event_type,actor_id,payload_revision,"
                        + "payload_sha256,before_state,after_state,details,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)")) {
            statement.setObject(1, event.historyId());
            statement.setObject(2, event.requestId().value());
            statement.setString(3, event.eventType().name());
            statement.setString(4, event.actorId().value());
            statement.setLong(5, event.payloadRevision());
            statement.setString(6, event.payloadSha256());
            setString(statement, 7, event.beforeState() == null ? null : event.beforeState().name());
            statement.setString(8, event.afterState().name());
            statement.setString(9, RequestJsonCodec.encode(event.details()));
            statement.setTimestamp(10, Timestamp.from(event.createdAt()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("History insert count != 1");
        }
    }

    private static void updateHead(Connection connection, RequestRecord updated, long expectedRevision) throws SQLException {
        if (updated.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("updated revision must equal expectedRevision + 1");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE requests.records SET state=?,active_payload_revision=?,active_payload_sha256=?,"
                        + "approved_payload_revision=?,approved_payload_sha256=?,updated_at=?,revision=? "
                        + "WHERE request_id=? AND revision=?")) {
            int i = 1;
            statement.setString(i++, updated.state().name());
            statement.setLong(i++, updated.activePayloadRevision());
            statement.setString(i++, updated.activePayloadSha256());
            setLong(statement, i++, updated.approvedPayloadRevision());
            setString(statement, i++, updated.approvedPayloadSha256());
            statement.setTimestamp(i++, Timestamp.from(updated.updatedAt()));
            statement.setLong(i++, updated.revision());
            statement.setObject(i++, updated.requestId().value());
            statement.setLong(i, expectedRevision);
            int count = statement.executeUpdate();
            if (count != 1) {
                if (findById(connection, updated.requestId()).isEmpty()) {
                    throw new RequestNotFoundException(updated.requestId());
                }
                throw new RequestRevisionConflictException(updated.requestId(), expectedRevision);
            }
        }
    }

    private static Optional<RequestRecord> findById(Connection connection, ApprovalRequestId requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + RECORD_COLUMNS + " FROM requests.records WHERE request_id=?")) {
            statement.setObject(1, requestId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapRequest(result)) : Optional.empty();
            }
        }
    }

    private static RequestRecord requireById(Connection connection, ApprovalRequestId requestId) throws SQLException {
        return findById(connection, requestId).orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    private static RequestRecord mapRequest(ResultSet result) throws SQLException {
        Long approvedRevision = result.getObject("approved_payload_revision") == null
                ? null
                : result.getLong("approved_payload_revision");
        Timestamp expires = result.getTimestamp("expires_at");
        return new RequestRecord(
                new ApprovalRequestId(result.getObject("request_id", UUID.class)),
                result.getString("request_type"),
                new ActorId(result.getString("requester_actor_id")),
                RequestState.valueOf(result.getString("state")),
                RequestRiskLevel.valueOf(result.getString("risk_level")),
                result.getInt("required_approvals"),
                result.getLong("active_payload_revision"),
                result.getString("active_payload_sha256"),
                approvedRevision,
                result.getString("approved_payload_sha256"),
                expires == null ? null : expires.toInstant(),
                DataClassification.valueOf(result.getString("classification")),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("revision"));
    }

    private static RequestPayload mapPayload(ResultSet result) throws SQLException {
        return new RequestPayload(
                new ApprovalRequestId(result.getObject("request_id", UUID.class)),
                result.getLong("payload_revision"),
                result.getString("content_type"),
                result.getString("payload_body"),
                result.getString("payload_sha256"),
                new ActorId(result.getString("revised_by")),
                result.getTimestamp("created_at").toInstant());
    }

    private static RequestApproval mapApproval(ResultSet result) throws SQLException {
        return new RequestApproval(
                result.getObject("approval_id", UUID.class),
                new ApprovalRequestId(result.getObject("request_id", UUID.class)),
                result.getLong("payload_revision"),
                result.getString("payload_sha256"),
                new ActorId(result.getString("approver_actor_id")),
                ApprovalDecision.valueOf(result.getString("decision")),
                result.getString("reason"),
                result.getTimestamp("created_at").toInstant());
    }

    private static RequestHistoryEvent mapHistory(ResultSet result) throws SQLException {
        String before = result.getString("before_state");
        return new RequestHistoryEvent(
                result.getObject("history_id", UUID.class),
                new ApprovalRequestId(result.getObject("request_id", UUID.class)),
                RequestHistoryEventType.valueOf(result.getString("event_type")),
                new ActorId(result.getString("actor_id")),
                result.getLong("payload_revision"),
                result.getString("payload_sha256"),
                before == null ? null : RequestState.valueOf(before),
                RequestState.valueOf(result.getString("after_state")),
                RequestJsonCodec.decode(result.getString("details_json")),
                result.getTimestamp("created_at").toInstant());
    }

    private static void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void setTimestamp(PreparedStatement statement, int index, java.time.Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }
}
