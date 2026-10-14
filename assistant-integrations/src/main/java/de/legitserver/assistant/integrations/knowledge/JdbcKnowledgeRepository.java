package de.legitserver.assistant.integrations.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.repository.KnowledgeDocumentNotFoundException;
import de.legitserver.assistant.knowledge.repository.KnowledgeRepository;
import de.legitserver.assistant.knowledge.repository.KnowledgeRevisionConflictException;
import de.legitserver.assistant.knowledge.repository.KnowledgeStatusConflictException;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchHit;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchPort;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL adapter for the Phase-7 Knowledge Catalog and V1 search contracts. */
public final class JdbcKnowledgeRepository implements KnowledgeRepository, KnowledgeSearchPort {
    private static final String DOCUMENT_COLUMNS =
            "document_id, domain, title, classification, status, current_revision, valid_from, "
                    + "required_permissions, created_by, created_at, updated_by, updated_at";
    private final TransactionRunner transactions;

    public JdbcKnowledgeRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void create(KnowledgeDocument document, KnowledgeRevision initialRevision) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(initialRevision, "initialRevision");
        if (!document.documentId().equals(initialRevision.documentId())) {
            throw new IllegalArgumentException("Document and initial revision IDs differ");
        }
        if (document.currentRevision() != initialRevision.revision()) {
            throw new IllegalArgumentException("Document currentRevision differs from initial revision");
        }
        transactions.required(connection -> {
            insertDocument(connection, document);
            insertRevision(connection, initialRevision);
            return null;
        });
    }

    @Override
    public Optional<KnowledgeDocument> findById(KnowledgeDocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return transactions.readOnly(connection -> findDocument(connection, documentId, false));
    }

    @Override
    public Optional<KnowledgeRevision> findRevision(KnowledgeDocumentId documentId, long revision) {
        Objects.requireNonNull(documentId, "documentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT document_id, revision, content, content_sha256, tags, metadata_json::text, "
                            + "source_kind, source_ref, source_label, created_by, created_at "
                            + "FROM knowledge.revisions WHERE document_id=? AND revision=?")) {
                statement.setObject(1, documentId.value());
                statement.setLong(2, revision);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapRevision(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public KnowledgeDocument appendRevision(
            KnowledgeRevision revision,
            long expectedCurrentRevision,
            ActorId updatedBy,
            Instant updatedAt) {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (expectedCurrentRevision < 1) {
            throw new IllegalArgumentException("expectedCurrentRevision must be >= 1");
        }
        if (revision.revision() != expectedCurrentRevision + 1) {
            throw new IllegalArgumentException("Revision number must equal expectedCurrentRevision + 1");
        }
        return transactions.required(connection -> {
            KnowledgeDocument current = findDocument(connection, revision.documentId(), true)
                    .orElseThrow(() -> new KnowledgeDocumentNotFoundException(revision.documentId()));
            if (current.currentRevision() != expectedCurrentRevision) {
                throw new KnowledgeRevisionConflictException(revision.documentId(), expectedCurrentRevision);
            }
            if (current.status() == KnowledgeDocumentStatus.ARCHIVED) {
                throw new IllegalStateException("Cannot revise archived Knowledge document");
            }
            insertRevision(connection, revision);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE knowledge.documents SET current_revision=?, status='DRAFT', valid_from=NULL, "
                            + "updated_by=?, updated_at=? WHERE document_id=? AND current_revision=?")) {
                statement.setLong(1, revision.revision());
                statement.setString(2, updatedBy.value());
                statement.setTimestamp(3, Timestamp.from(updatedAt));
                statement.setObject(4, revision.documentId().value());
                statement.setLong(5, expectedCurrentRevision);
                if (statement.executeUpdate() != 1) {
                    throw new KnowledgeRevisionConflictException(revision.documentId(), expectedCurrentRevision);
                }
            }
            return findDocument(connection, revision.documentId(), false)
                    .orElseThrow(() -> new KnowledgeDocumentNotFoundException(revision.documentId()));
        });
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
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE knowledge.documents SET status=?, valid_from=?, updated_by=?, updated_at=? "
                            + "WHERE document_id=? AND current_revision=? AND status=?")) {
                statement.setString(1, newStatus.name());
                setInstant(statement, 2, validFrom);
                statement.setString(3, updatedBy.value());
                statement.setTimestamp(4, Timestamp.from(updatedAt));
                statement.setObject(5, documentId.value());
                statement.setLong(6, expectedCurrentRevision);
                statement.setString(7, expectedStatus.name());
                if (statement.executeUpdate() != 1) {
                    Optional<KnowledgeDocument> current = findDocument(connection, documentId, false);
                    if (current.isEmpty()) throw new KnowledgeDocumentNotFoundException(documentId);
                    if (current.get().currentRevision() != expectedCurrentRevision) {
                        throw new KnowledgeRevisionConflictException(documentId, expectedCurrentRevision);
                    }
                    throw new KnowledgeStatusConflictException(documentId, expectedStatus);
                }
            }
            return findDocument(connection, documentId, false)
                    .orElseThrow(() -> new KnowledgeDocumentNotFoundException(documentId));
        });
    }

    @Override
    public KnowledgeSearchResult search(KnowledgeSearchQuery query, KnowledgeAccessContext accessContext) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(accessContext, "accessContext");
        if (!accessContext.queryAuthorized() || accessContext.allowedDomains().isEmpty()) {
            return KnowledgeSearchResult.noReliableResult();
        }
        Set<KnowledgeDomain> effectiveDomains = new LinkedHashSet<>(accessContext.allowedDomains());
        if (!query.domains().isEmpty()) effectiveDomains.retainAll(query.domains());
        if (effectiveDomains.isEmpty()) return KnowledgeSearchResult.noReliableResult();
        Set<DataClassification> allowedClassifications = allowedClassifications(accessContext.maximumClassification());
        return transactions.readOnly(connection -> executeSearch(
                connection, query, accessContext, effectiveDomains, allowedClassifications));
    }

    private static KnowledgeSearchResult executeSearch(
            Connection connection,
            KnowledgeSearchQuery query,
            KnowledgeAccessContext accessContext,
            Set<KnowledgeDomain> domains,
            Set<DataClassification> classifications)
            throws SQLException {
        boolean fullText = query.text() != null;
        StringBuilder sql = new StringBuilder(
                "SELECT d.document_id, d.current_revision, d.domain, d.title, d.classification, "
                        + "r.content, r.content_sha256, r.tags, r.metadata_json::text, "
                        + "r.source_kind, r.source_ref, r.source_label, ");
        if (fullText) {
            sql.append("(2.0 * ts_rank_cd(d.title_search_vector, websearch_to_tsquery('simple', ?)) "
                    + "+ ts_rank_cd(r.search_vector, websearch_to_tsquery('simple', ?))) AS relevance ");
        } else {
            sql.append("0.0::double precision AS relevance ");
        }
        sql.append("FROM knowledge.documents d JOIN knowledge.revisions r "
                + "ON r.document_id=d.document_id AND r.revision=d.current_revision "
                + "WHERE d.status='PUBLISHED' AND d.valid_from <= CURRENT_TIMESTAMP "
                + "AND d.domain::text = ANY (?) AND d.classification::text = ANY (?) "
                + "AND d.required_permissions <@ ? ");
        if (fullText) {
            sql.append("AND (d.title_search_vector @@ websearch_to_tsquery('simple', ?) "
                    + "OR r.search_vector @@ websearch_to_tsquery('simple', ?)) ");
        }
        if (!query.tags().isEmpty()) sql.append("AND r.tags @> ? ");
        if (!query.metadata().isEmpty()) sql.append("AND r.metadata_json @> CAST(? AS jsonb) ");
        sql.append("ORDER BY relevance DESC, d.updated_at DESC, d.document_id ASC LIMIT ?");

        List<KnowledgeSearchHit> hits = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (fullText) {
                statement.setString(index++, query.text());
                statement.setString(index++, query.text());
            }
            statement.setArray(index++, textArray(connection, domains.stream().map(Enum::name).toList()));
            statement.setArray(index++, textArray(connection, classifications.stream().map(Enum::name).toList()));
            statement.setArray(index++, textArray(connection, accessContext.effectivePermissions().stream().sorted().toList()));
            if (fullText) {
                statement.setString(index++, query.text());
                statement.setString(index++, query.text());
            }
            if (!query.tags().isEmpty()) {
                statement.setArray(index++, textArray(connection, query.tags().stream().sorted().toList()));
            }
            if (!query.metadata().isEmpty()) {
                statement.setString(index++, KnowledgeJsonCodec.encode(query.metadata()));
            }
            statement.setInt(index, query.limit());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) hits.add(mapHit(result));
            }
        }
        return hits.isEmpty() ? KnowledgeSearchResult.noReliableResult() : KnowledgeSearchResult.results(hits);
    }

    private static void insertDocument(Connection connection, KnowledgeDocument document) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO knowledge.documents(document_id, domain, title, classification, status, "
                        + "current_revision, valid_from, required_permissions, created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int index = 1;
            statement.setObject(index++, document.documentId().value());
            statement.setString(index++, document.domain().name());
            statement.setString(index++, document.title());
            statement.setString(index++, document.classification().name());
            statement.setString(index++, document.status().name());
            statement.setLong(index++, document.currentRevision());
            setInstant(statement, index++, document.validFrom());
            statement.setArray(index++, textArray(connection, document.acl().requiredPermissions().stream().sorted().toList()));
            statement.setString(index++, document.createdBy().value());
            statement.setTimestamp(index++, Timestamp.from(document.createdAt()));
            statement.setString(index++, document.updatedBy().value());
            statement.setTimestamp(index, Timestamp.from(document.updatedAt()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Knowledge document insert count != 1");
        }
    }

    private static void insertRevision(Connection connection, KnowledgeRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO knowledge.revisions(document_id, revision, content, content_sha256, tags, metadata_json, "
                        + "source_kind, source_ref, source_label, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)")) {
            int index = 1;
            statement.setObject(index++, revision.documentId().value());
            statement.setLong(index++, revision.revision());
            statement.setString(index++, revision.content());
            statement.setString(index++, revision.contentSha256());
            statement.setArray(index++, textArray(connection, revision.tags().stream().sorted().toList()));
            statement.setString(index++, KnowledgeJsonCodec.encode(revision.metadata()));
            statement.setString(index++, revision.source().sourceKind());
            statement.setString(index++, revision.source().sourceRef());
            statement.setString(index++, revision.source().sourceLabel());
            statement.setString(index++, revision.createdBy().value());
            statement.setTimestamp(index, Timestamp.from(revision.createdAt()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Knowledge revision insert count != 1");
        }
    }

    private static Optional<KnowledgeDocument> findDocument(
            Connection connection, KnowledgeDocumentId documentId, boolean forUpdate) throws SQLException {
        String sql = "SELECT " + DOCUMENT_COLUMNS + " FROM knowledge.documents WHERE document_id=?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, documentId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapDocument(result)) : Optional.empty();
            }
        }
    }

    private static KnowledgeDocument mapDocument(ResultSet result) throws SQLException {
        return new KnowledgeDocument(
                new KnowledgeDocumentId(result.getObject("document_id", UUID.class)),
                KnowledgeDomain.valueOf(result.getString("domain")),
                result.getString("title"),
                DataClassification.valueOf(result.getString("classification")),
                KnowledgeDocumentStatus.valueOf(result.getString("status")),
                result.getLong("current_revision"),
                toInstant(result.getTimestamp("valid_from")),
                new ActorId(result.getString("created_by")),
                result.getTimestamp("created_at").toInstant(),
                new ActorId(result.getString("updated_by")),
                result.getTimestamp("updated_at").toInstant(),
                new KnowledgeAcl(arrayStrings(result.getArray("required_permissions"))));
    }

    private static KnowledgeRevision mapRevision(ResultSet result) throws SQLException {
        return new KnowledgeRevision(
                new KnowledgeDocumentId(result.getObject("document_id", UUID.class)),
                result.getLong("revision"),
                result.getString("content"),
                result.getString("content_sha256"),
                arrayStrings(result.getArray("tags")),
                KnowledgeJsonCodec.decode(result.getString("metadata_json")),
                new KnowledgeSourceAttribution(
                        result.getString("source_kind"),
                        result.getString("source_ref"),
                        result.getString("source_label")),
                new ActorId(result.getString("created_by")),
                result.getTimestamp("created_at").toInstant());
    }

    private static KnowledgeSearchHit mapHit(ResultSet result) throws SQLException {
        return new KnowledgeSearchHit(
                new KnowledgeDocumentId(result.getObject("document_id", UUID.class)),
                result.getLong("current_revision"),
                KnowledgeDomain.valueOf(result.getString("domain")),
                result.getString("title"),
                DataClassification.valueOf(result.getString("classification")),
                result.getString("content"),
                result.getString("content_sha256"),
                arrayStrings(result.getArray("tags")),
                KnowledgeJsonCodec.decode(result.getString("metadata_json")),
                new KnowledgeSourceAttribution(
                        result.getString("source_kind"),
                        result.getString("source_ref"),
                        result.getString("source_label")),
                result.getDouble("relevance"));
    }

    private static Set<DataClassification> allowedClassifications(DataClassification maximum) {
        EnumSet<DataClassification> result = EnumSet.noneOf(DataClassification.class);
        for (DataClassification value : DataClassification.values()) {
            if (maximum.isAtLeast(value)) result.add(value);
        }
        return result;
    }

    private static Array textArray(Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray(String[]::new));
    }

    private static Set<String> arrayStrings(Array array) throws SQLException {
        if (array == null) return Set.of();
        Object raw = array.getArray();
        if (raw instanceof String[] strings) return Set.of(strings);
        Object[] values = (Object[]) raw;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) result.add(String.valueOf(value));
        return Set.copyOf(result);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }
}
