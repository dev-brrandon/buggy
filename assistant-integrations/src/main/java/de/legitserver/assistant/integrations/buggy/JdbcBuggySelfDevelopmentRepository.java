package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalBinding;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentChangeType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentContentPolicy;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidenceType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
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

/** PostgreSQL B7 adapter for append-only Self-Development revisions/evidence and mutable head pointers. */
public final class JdbcBuggySelfDevelopmentRepository implements BuggySelfDevelopmentRepository {
    private final TransactionRunner transactions;
    private final BuggySelfDevelopmentContentPolicy contentPolicy;

    public JdbcBuggySelfDevelopmentRepository(
            TransactionRunner transactions,
            BuggySelfDevelopmentContentPolicy contentPolicy) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
    }

    @Override
    public Optional<BuggySelfDevelopmentRevision> findCurrent(BuggySelfDevelopmentId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return transactions.readOnly(connection -> findCurrent(connection, itemId));
    }

    @Override
    public List<BuggySelfDevelopmentRevision> listCurrentItems(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(connection -> {
            ArrayList<BuggySelfDevelopmentRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT r.* FROM assistant.buggy_selfdev_items i JOIN assistant.buggy_selfdev_revisions r "
                            + "ON r.assistant_id=i.assistant_id AND r.item_id=i.item_id AND r.item_revision=i.current_revision "
                            + "WHERE i.assistant_id='BUGGY' ORDER BY r.changed_at DESC,r.item_id LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) result.add(mapRevision(rs)); }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public List<BuggySelfDevelopmentRevision> history(BuggySelfDevelopmentId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return transactions.readOnly(connection -> {
            ArrayList<BuggySelfDevelopmentRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_selfdev_revisions "
                            + "WHERE assistant_id='BUGGY' AND item_id=? ORDER BY item_revision")) {
                statement.setObject(1, itemId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapRevision(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public BuggySelfDevelopmentRevision append(long expectedCurrentRevision, BuggySelfDevelopmentRevision revision) {
        Objects.requireNonNull(revision, "revision");
        if (expectedCurrentRevision < 0) throw new IllegalArgumentException("expectedCurrentRevision must be >= 0");
        validateText(revision);
        return transactions.required(connection -> {
            lock(connection, revision.itemId());
            long actual = findCurrent(connection, revision.itemId())
                    .map(BuggySelfDevelopmentRevision::revision).orElse(0L);
            if (actual != expectedCurrentRevision) throw new IllegalStateException("Stale Buggy Self-Development revision");
            if (revision.revision() != expectedCurrentRevision + 1) {
                throw new IllegalArgumentException("Self-Development revision does not continue active head");
            }
            insertRevision(connection, revision);
            if (expectedCurrentRevision == 0) insertHead(connection, revision);
            else updateHead(connection, expectedCurrentRevision, revision);
            return findCurrent(connection, revision.itemId()).orElseThrow();
        });
    }

    @Override
    public void appendEvidence(BuggySelfDevelopmentEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.buggy_selfdev_evidence(evidence_id,assistant_id,item_id,item_revision,evidence_type,"
                            + "tool_id,reference_id,sha256,successful,observed_by,observed_at) "
                            + "VALUES (?,'BUGGY',?,?,?,?,?,?,?,?,?)")) {
                statement.setObject(1, evidence.evidenceId());
                statement.setObject(2, evidence.itemId().value());
                statement.setLong(3, evidence.itemRevision());
                statement.setString(4, evidence.type().name());
                statement.setString(5, evidence.toolId() == null ? null : evidence.toolId().value());
                statement.setString(6, evidence.referenceId());
                statement.setString(7, evidence.sha256());
                statement.setBoolean(8, evidence.successful());
                statement.setString(9, evidence.observedBy().value());
                statement.setTimestamp(10, Timestamp.from(evidence.observedAt()));
                requireOne(statement.executeUpdate(), "Self-Development evidence insert");
            }
            return null;
        });
    }

    @Override
    public List<BuggySelfDevelopmentEvidence> evidence(BuggySelfDevelopmentId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return transactions.readOnly(connection -> {
            ArrayList<BuggySelfDevelopmentEvidence> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT evidence_id,item_id,item_revision,evidence_type,tool_id,reference_id,sha256,successful,observed_by,observed_at "
                            + "FROM assistant.buggy_selfdev_evidence WHERE assistant_id='BUGGY' AND item_id=? "
                            + "ORDER BY observed_at,evidence_id")) {
                statement.setObject(1, itemId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapEvidence(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    private void validateText(BuggySelfDevelopmentRevision revision) {
        contentPolicy.requirePersistableText(revision.title(), "title", 256);
        contentPolicy.requirePersistableText(revision.problem(), "problem", 8192);
        if (revision.proposalSummary() != null) {
            contentPolicy.requirePersistableText(revision.proposalSummary(), "proposalSummary", 8192);
        }
        if (revision.reason() != null) contentPolicy.requirePersistableText(revision.reason(), "reason", 4096);
    }

    private static Optional<BuggySelfDevelopmentRevision> findCurrent(
            Connection connection, BuggySelfDevelopmentId itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.* FROM assistant.buggy_selfdev_items i JOIN assistant.buggy_selfdev_revisions r "
                        + "ON r.assistant_id=i.assistant_id AND r.item_id=i.item_id AND r.item_revision=i.current_revision "
                        + "WHERE i.assistant_id='BUGGY' AND i.item_id=?")) {
            statement.setObject(1, itemId.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRevision(rs)) : Optional.empty();
            }
        }
    }

    private static void insertRevision(Connection connection, BuggySelfDevelopmentRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_selfdev_revisions(assistant_id,item_id,item_revision,previous_revision,state,title,problem,"
                        + "proposal_summary,change_type,source_component,source_version,source_build,source_revision,source_created_at,"
                        + "source_checksum,workspace_ref,patch_ref,build_artifact_ref,risk_level,approval_request_id,"
                        + "approval_submitted_item_revision,approval_request_type,approval_payload_sha256,approval_risk_level,"
                        + "reason,changed_by,changed_at) VALUES ('BUGGY',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            statement.setObject(i++, revision.itemId().value());
            statement.setLong(i++, revision.revision());
            if (revision.previousRevision() == null) statement.setObject(i++, null);
            else statement.setLong(i++, revision.previousRevision());
            statement.setString(i++, revision.state().name());
            statement.setString(i++, revision.title());
            statement.setString(i++, revision.problem());
            statement.setString(i++, revision.proposalSummary());
            statement.setString(i++, revision.changeType() == null ? null : revision.changeType().name());
            SourceSnapshot source = revision.sourceSnapshot();
            statement.setString(i++, source.component());
            statement.setString(i++, source.version());
            statement.setString(i++, source.build());
            statement.setString(i++, source.revision());
            statement.setTimestamp(i++, Timestamp.from(source.createdAt()));
            statement.setString(i++, source.checksum());
            statement.setString(i++, revision.workspaceRef());
            statement.setString(i++, revision.patchRef());
            statement.setString(i++, revision.buildArtifactRef());
            statement.setString(i++, revision.riskLevel() == null ? null : revision.riskLevel().name());
            BuggySelfDevelopmentApprovalBinding binding = revision.approvalBinding();
            statement.setObject(i++, binding == null ? null : binding.requestId().value());
            if (binding == null) statement.setObject(i++, null);
            else statement.setLong(i++, binding.submittedItemRevision());
            statement.setString(i++, binding == null ? null : binding.requestType());
            statement.setString(i++, binding == null ? null : binding.payloadSha256());
            statement.setString(i++, binding == null ? null : binding.riskLevel().name());
            statement.setString(i++, revision.reason());
            statement.setString(i++, revision.changedBy().value());
            statement.setTimestamp(i, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Self-Development revision insert");
        }
    }

    private static void insertHead(Connection connection, BuggySelfDevelopmentRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_selfdev_items(assistant_id,item_id,current_revision,created_at,updated_at) "
                        + "VALUES ('BUGGY',?,?,?,?)")) {
            statement.setObject(1, revision.itemId().value());
            statement.setLong(2, revision.revision());
            statement.setTimestamp(3, Timestamp.from(revision.changedAt()));
            statement.setTimestamp(4, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Self-Development item insert");
        }
    }

    private static void updateHead(
            Connection connection, long expectedCurrentRevision, BuggySelfDevelopmentRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_selfdev_items SET current_revision=?,updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND item_id=? AND current_revision=?")) {
            statement.setLong(1, revision.revision());
            statement.setTimestamp(2, Timestamp.from(revision.changedAt()));
            statement.setObject(3, revision.itemId().value());
            statement.setLong(4, expectedCurrentRevision);
            requireOne(statement.executeUpdate(), "Self-Development head update");
        }
    }

    private static BuggySelfDevelopmentRevision mapRevision(ResultSet rs) throws SQLException {
        long previous = rs.getLong("previous_revision");
        Long previousRevision = rs.wasNull() ? null : previous;
        String change = rs.getString("change_type");
        String risk = rs.getString("risk_level");
        UUID approvalId = rs.getObject("approval_request_id", UUID.class);
        BuggySelfDevelopmentApprovalBinding binding = null;
        if (approvalId != null) {
            binding = new BuggySelfDevelopmentApprovalBinding(
                    new ApprovalRequestId(approvalId),
                    rs.getLong("approval_submitted_item_revision"),
                    rs.getString("approval_request_type"),
                    rs.getString("approval_payload_sha256"),
                    ToolRiskLevel.valueOf(rs.getString("approval_risk_level")));
        }
        SourceSnapshot source = new SourceSnapshot(
                rs.getString("source_component"),
                rs.getString("source_version"),
                rs.getString("source_build"),
                rs.getString("source_revision"),
                rs.getTimestamp("source_created_at").toInstant(),
                rs.getString("source_checksum"));
        return new BuggySelfDevelopmentRevision(
                new BuggySelfDevelopmentId(rs.getObject("item_id", UUID.class)),
                rs.getLong("item_revision"),
                previousRevision,
                BuggySelfDevelopmentState.valueOf(rs.getString("state")),
                rs.getString("title"),
                rs.getString("problem"),
                rs.getString("proposal_summary"),
                change == null ? null : BuggySelfDevelopmentChangeType.valueOf(change),
                source,
                rs.getString("workspace_ref"),
                rs.getString("patch_ref"),
                rs.getString("build_artifact_ref"),
                risk == null ? null : ToolRiskLevel.valueOf(risk),
                binding,
                rs.getString("reason"),
                new ActorId(rs.getString("changed_by")),
                rs.getTimestamp("changed_at").toInstant());
    }

    private static BuggySelfDevelopmentEvidence mapEvidence(ResultSet rs) throws SQLException {
        String toolId = rs.getString("tool_id");
        return new BuggySelfDevelopmentEvidence(
                rs.getObject("evidence_id", UUID.class),
                new BuggySelfDevelopmentId(rs.getObject("item_id", UUID.class)),
                rs.getLong("item_revision"),
                BuggySelfDevelopmentEvidenceType.valueOf(rs.getString("evidence_type")),
                toolId == null ? null : new ToolId(toolId),
                rs.getString("reference_id"),
                rs.getString("sha256"),
                rs.getBoolean("successful"),
                new ActorId(rs.getString("observed_by")),
                rs.getTimestamp("observed_at").toInstant());
    }

    private static void lock(Connection connection, BuggySelfDevelopmentId itemId) throws SQLException {
        UUID id = itemId.value();
        long key = id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ 0x4255474759423037L;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Self-Development advisory lock returned no row");
            }
        }
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected " + count + " rows");
    }
}
