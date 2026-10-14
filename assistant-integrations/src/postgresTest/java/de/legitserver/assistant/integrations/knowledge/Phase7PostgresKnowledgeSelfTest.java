package de.legitserver.assistant.integrations.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;
import de.legitserver.assistant.knowledge.model.KnowledgeAcl;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentDraft;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.policy.KnowledgePublicationWorkflow;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchQuery;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchResult;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus;
import de.legitserver.assistant.knowledge.service.KnowledgeCatalogService;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL 18.x verification for the Phase-7 Knowledge Foundation. */
public final class Phase7PostgresKnowledgeSelfTest {
    private static final ActorId OWNER = new ActorId("actor.phase7.postgres");
    private static final Instant FIXED_TIME = Instant.parse("2026-10-04T06:00:00Z");
    private static final KnowledgeSourceAttribution SOURCE =
            new KnowledgeSourceAttribution("PROJECT_FILE", "phase7/live-test", "Phase 7 live test");
    private static final String TECH_PERMISSION = "networkcore.docs.technical";

    private Phase7PostgresKnowledgeSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        JdbcTransactionRunner transactions =
                new JdbcTransactionRunner(new DriverManagerDataSource(args[0], args[1], args[2]));
        JdbcKnowledgeRepository repository = new JdbcKnowledgeRepository(transactions);
        KnowledgeCatalogService service = new KnowledgeCatalogService(
                repository,
                repository,
                new KnowledgePublicationWorkflow(),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        truncateKnowledge(transactions);
        verifyMigrationAndIndexes(transactions);
        verifyLifecycleAclPublishedFilteringAndAttribution(service, repository);
        verifyDatabaseRevisionImmutability(transactions, repository);
        verifyNoReliableResult(service);
        verifySearchPerformance(transactions, repository);
        truncateKnowledge(transactions);
        System.out.println("PHASE7_POSTGRES_KNOWLEDGE_SELF_TEST: PASS");
    }

    private static void verifyMigrationAndIndexes(JdbcTransactionRunner transactions) {
        String seed = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT metadata_value FROM assistant.platform_metadata WHERE metadata_key='foundation.knowledge.version'")) {
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next(), "knowledge foundation seed missing");
                    return result.getString(1);
                }
            }
        });
        require("phase7".equals(seed), "knowledge foundation seed must be phase7");

        Set<String> expected = Set.of(
                "ix_knowledge_documents_title_fts",
                "ix_knowledge_documents_acl",
                "ix_knowledge_revisions_fts",
                "ix_knowledge_revisions_tags",
                "ix_knowledge_revisions_metadata");
        Set<String> present = transactions.readOnly(connection -> {
            java.util.HashSet<String> names = new java.util.HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT indexname FROM pg_indexes WHERE schemaname='knowledge'")) {
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) names.add(result.getString(1));
                }
            }
            return Set.copyOf(names);
        });
        java.util.HashSet<String> missing = new java.util.HashSet<>(expected);
        missing.removeAll(present);
        require(missing.isEmpty(), "knowledge search/ACL indexes missing: " + missing);
    }

    private static void verifyLifecycleAclPublishedFilteringAndAttribution(
            KnowledgeCatalogService service, JdbcKnowledgeRepository repository) {
        KnowledgeDocument open = service.create(
                new KnowledgeDocumentDraft(
                        KnowledgeDomain.TROUBLESHOOTING,
                        "Velocity forwarding secret recovery",
                        DataClassification.INTERNAL,
                        KnowledgeAcl.openWithinPolicy(),
                        "Validate the Velocity forwarding secret before restarting the proxy.",
                        Set.of("velocity", "proxy"),
                        Map.of("component", "networkcore", "environment", "production"),
                        SOURCE),
                OWNER);
        require(search(service, "forwarding secret", baseAccess()).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "draft document leaked into standard retrieval");
        service.submitForReview(open.documentId(), 1, OWNER);
        KnowledgeDocument published = service.publish(open.documentId(), 1, OWNER);
        require(published.status() == KnowledgeDocumentStatus.PUBLISHED, "document did not publish");
        KnowledgeSearchResult publishedResult = service.search(
                new KnowledgeSearchQuery(
                        "forwarding secret",
                        Set.of(KnowledgeDomain.TROUBLESHOOTING),
                        Set.of("velocity"),
                        Map.of("component", "networkcore"),
                        20),
                baseAccess());
        require(publishedResult.status() == KnowledgeSearchStatus.RESULTS, "published FTS/tag/metadata query failed");
        require(publishedResult.hits().stream().anyMatch(hit -> hit.documentId().equals(open.documentId())),
                "published document missing from result");
        require(publishedResult.hits().stream()
                        .filter(hit -> hit.documentId().equals(open.documentId()))
                        .findFirst()
                        .orElseThrow()
                        .source()
                        .sourceRef()
                        .equals("phase7/live-test"),
                "source attribution missing");

        KnowledgeDocument restricted = service.create(
                new KnowledgeDocumentDraft(
                        KnowledgeDomain.TROUBLESHOOTING,
                        "Technical deployment token procedure",
                        DataClassification.INTERNAL,
                        new KnowledgeAcl(Set.of(TECH_PERMISSION)),
                        "Technical procedure for deployment token rotation without exposing token values.",
                        Set.of("deployment"),
                        Map.of("component", "panel"),
                        SOURCE),
                OWNER);
        service.submitForReview(restricted.documentId(), 1, OWNER);
        service.publish(restricted.documentId(), 1, OWNER);
        KnowledgeSearchQuery restrictedQuery =
                new KnowledgeSearchQuery("deployment token", Set.of(), Set.of(), Map.of(), 20);
        require(service.search(restrictedQuery, baseAccess()).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "document ACL failed closed filtering");
        KnowledgeAccessContext technicalAccess = new KnowledgeAccessContext(
                OWNER,
                true,
                Set.of(TECH_PERMISSION),
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                DataClassification.INTERNAL);
        require(service.search(restrictedQuery, technicalAccess).status() == KnowledgeSearchStatus.RESULTS,
                "document ACL did not allow required permission");

        KnowledgeDocument secret = service.create(
                new KnowledgeDocumentDraft(
                        KnowledgeDomain.TROUBLESHOOTING,
                        "Secret-only recovery reference",
                        DataClassification.SECRET,
                        KnowledgeAcl.openWithinPolicy(),
                        "secretclassificationprobe recovery reference",
                        Set.of(),
                        Map.of(),
                        SOURCE),
                OWNER);
        service.submitForReview(secret.documentId(), 1, OWNER);
        service.publish(secret.documentId(), 1, OWNER);
        require(search(service, "secretclassificationprobe", baseAccess()).status()
                        == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "classification ceiling leaked SECRET document");

        KnowledgeRevision revisionOne = repository.findRevision(open.documentId(), 1).orElseThrow();
        KnowledgeDocument revised = service.addRevision(
                open.documentId(),
                1,
                "Validate forwarding configuration and maintenance window before proxy restart. phase7revisiontwo",
                Set.of("velocity", "proxy"),
                Map.of("component", "networkcore", "environment", "production"),
                new KnowledgeSourceAttribution("PROJECT_FILE", "phase7/revision-two", "Revision two"),
                OWNER);
        require(revised.status() == KnowledgeDocumentStatus.DRAFT, "new revision must demote PUBLISHED to DRAFT");
        require(revised.currentRevision() == 2, "current revision not advanced");
        require(search(service, "phase7revisiontwo", baseAccess()).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "unreviewed revision leaked into retrieval");
        require(repository.findRevision(open.documentId(), 1).orElseThrow().equals(revisionOne),
                "old immutable revision changed");
        service.submitForReview(open.documentId(), 2, OWNER);
        service.publish(open.documentId(), 2, OWNER);
        KnowledgeSearchResult revisedResult = search(service, "phase7revisiontwo", baseAccess());
        require(revisedResult.status() == KnowledgeSearchStatus.RESULTS, "republished current revision missing");
        require(revisedResult.hits().getFirst().revision() == 2, "retrieval did not use current revision");
        require(revisedResult.hits().getFirst().source().sourceRef().equals("phase7/revision-two"),
                "revised source attribution missing");
    }

    private static void verifyDatabaseRevisionImmutability(
            JdbcTransactionRunner transactions, JdbcKnowledgeRepository repository) {
        KnowledgeDocument target = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT document_id FROM knowledge.documents ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next(), "immutability target missing");
                    return repository.findById(new KnowledgeDocumentId(result.getObject(1, UUID.class))).orElseThrow();
                }
            }
        });
        KnowledgeRevision before = repository.findRevision(target.documentId(), 1).orElseThrow();
        expectPersistenceFailure(() -> transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE knowledge.revisions SET content='tampered' WHERE document_id=? AND revision=1")) {
                statement.setObject(1, target.documentId().value());
                statement.executeUpdate();
            }
            return null;
        }));
        expectPersistenceFailure(() -> transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM knowledge.revisions WHERE document_id=? AND revision=1")) {
                statement.setObject(1, target.documentId().value());
                statement.executeUpdate();
            }
            return null;
        }));
        require(repository.findRevision(target.documentId(), 1).orElseThrow().equals(before),
                "DB mutation attempt changed immutable revision");
    }

    private static void verifyNoReliableResult(KnowledgeCatalogService service) {
        KnowledgeSearchResult missing = search(service, "no-such-phase7-document-7f7f7f", baseAccess());
        require(missing.status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT, "missing query did not return no-result");
        require(missing.hits().isEmpty(), "no-result contained invented hits");
        KnowledgeAccessContext unauthorized = new KnowledgeAccessContext(
                OWNER,
                false,
                Set.of(TECH_PERMISSION),
                Set.of(KnowledgeDomain.values()),
                DataClassification.SECRET);
        require(search(service, "forwarding", unauthorized).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                "unauthorized query returned Knowledge");
    }

    private static void verifySearchPerformance(
            JdbcTransactionRunner transactions, JdbcKnowledgeRepository repository) {
        seedPerformanceFixture(transactions, 800);
        KnowledgeAccessContext access = new KnowledgeAccessContext(
                OWNER,
                true,
                Set.of(),
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                DataClassification.PUBLIC);
        KnowledgeSearchQuery query = new KnowledgeSearchQuery(
                "phase7performanceprobe",
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                Set.of("perf"),
                Map.of("fixture", "phase7"),
                50);
        for (int i = 0; i < 5; i++) repository.search(query, access);
        List<Long> nanos = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long started = System.nanoTime();
            KnowledgeSearchResult result = repository.search(query, access);
            nanos.add(System.nanoTime() - started);
            require(result.status() == KnowledgeSearchStatus.RESULTS, "performance fixture query returned no result");
            require(result.hits().size() <= 50, "search exceeded hard result limit");
        }
        Collections.sort(nanos);
        long p95Nanos = nanos.get((int) Math.ceil(nanos.size() * 0.95d) - 1);
        Duration p95 = Duration.ofNanos(p95Nanos);
        require(p95.compareTo(Duration.ofMillis(150)) < 0,
                "Knowledge search p95 exceeded 150 ms fixture budget: " + p95.toMillis() + " ms");
        System.out.println("PHASE7_KNOWLEDGE_SEARCH_P95_MS=" + p95.toMillis());
    }

    private static void seedPerformanceFixture(JdbcTransactionRunner transactions, int count) {
        transactions.required(connection -> {
            String documentSql = "INSERT INTO knowledge.documents(document_id, domain, title, classification, status, "
                    + "current_revision, valid_from, required_permissions, created_by, created_at, updated_by, updated_at) "
                    + "VALUES (?, 'TROUBLESHOOTING', ?, 'PUBLIC', 'PUBLISHED', 1, ?, ARRAY[]::text[], ?, ?, ?, ?)";
            String revisionSql = "INSERT INTO knowledge.revisions(document_id, revision, content, content_sha256, tags, "
                    + "metadata_json, source_kind, source_ref, source_label, created_by, created_at) "
                    + "VALUES (?, 1, ?, ?, ARRAY['perf']::text[], '{\"fixture\":\"phase7\"}'::jsonb, "
                    + "'GENERATED_TEST', ?, 'Performance fixture', ?, ?)";
            try (PreparedStatement documents = connection.prepareStatement(documentSql);
                    PreparedStatement revisions = connection.prepareStatement(revisionSql)) {
                for (int i = 0; i < count; i++) {
                    UUID id = UUID.randomUUID();
                    String content = "phase7performanceprobe indexed troubleshooting content number " + i;
                    Timestamp time = Timestamp.from(FIXED_TIME.plusSeconds(i));
                    documents.setObject(1, id);
                    documents.setString(2, "Performance knowledge " + i);
                    documents.setTimestamp(3, time);
                    documents.setString(4, OWNER.value());
                    documents.setTimestamp(5, time);
                    documents.setString(6, OWNER.value());
                    documents.setTimestamp(7, time);
                    documents.addBatch();

                    revisions.setObject(1, id);
                    revisions.setString(2, content);
                    revisions.setString(3, KnowledgeValidation.sha256Of(content));
                    revisions.setString(4, "phase7/perf/" + i);
                    revisions.setString(5, OWNER.value());
                    revisions.setTimestamp(6, time);
                    revisions.addBatch();
                }
                documents.executeBatch();
                revisions.executeBatch();
            }
            return null;
        });
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE knowledge.documents");
                statement.execute("ANALYZE knowledge.revisions");
            }
            return null;
        });
    }

    private static KnowledgeSearchResult search(
            KnowledgeCatalogService service, String text, KnowledgeAccessContext access) {
        return service.search(new KnowledgeSearchQuery(text, Set.of(), Set.of(), Map.of(), 50), access);
    }

    private static KnowledgeAccessContext baseAccess() {
        return new KnowledgeAccessContext(
                OWNER,
                true,
                Set.of(),
                Set.of(KnowledgeDomain.TROUBLESHOOTING),
                DataClassification.INTERNAL);
    }

    private static void truncateKnowledge(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE knowledge.revisions, knowledge.documents CASCADE");
            }
            return null;
        });
    }

    private static void expectPersistenceFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected immutable revision mutation to fail");
        } catch (RuntimeException expected) {
            require(expected.getMessage() != null, "mutation failure must contain diagnostic context");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String user;
        private final String password;

        private DriverManagerDataSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }

        @Override
        public Connection getConnection(String username, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, suppliedPassword);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("de.legitserver.assistant.integrations.knowledge");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
