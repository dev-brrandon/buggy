package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAccess;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeArea;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConfidence;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConflictStatus;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeDraft;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProfile;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeProvenance;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeSearchRequest;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeState;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeTopic;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeVisibility;
import de.legitserver.assistant.buggy.knowledge.BuggyOwnerDecisionDraft;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyKnowledgeService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.integrations.knowledge.JdbcKnowledgeRepository;
import de.legitserver.assistant.knowledge.access.KnowledgeAccessPolicy;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.policy.KnowledgePublicationWorkflow;
import de.legitserver.assistant.knowledge.search.KnowledgeSearchStatus;
import de.legitserver.assistant.knowledge.service.KnowledgeCatalogService;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL B4 regression. Run only against the dedicated Assistant test database. */
public final class B4BuggyKnowledgePostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T21:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b4-postgres-owner");
    private static final ActorId OTHER = new ActorId("actor:buggy-b4-postgres-other");
    private static final ActorId SYSTEM = new ActorId("system:buggy-b4-postgres-test");
    private static final Set<String> READ_PERMISSIONS = Set.of(
            BuggyKnowledgeProfile.KNOWLEDGE_READ_PERMISSION,
            BuggyKnowledgeProfile.SECURITY_READ_PERMISSION);

    private B4BuggyKnowledgePostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        FoundationSecretGuardedKnowledgeContentPolicy contentPolicy =
                new FoundationSecretGuardedKnowledgeContentPolicy(new SecretGuard());
        JdbcBuggyIdentityRepository identityRepository = new JdbcBuggyIdentityRepository(transactions);
        BuggyIdentityService identity = new BuggyIdentityService(identityRepository);
        JdbcKnowledgeRepository knowledgeRepository = new JdbcKnowledgeRepository(transactions);
        KnowledgeCatalogService catalog = new KnowledgeCatalogService(
                knowledgeRepository,
                knowledgeRepository,
                new KnowledgePublicationWorkflow(),
                Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        JdbcBuggyKnowledgeAnnotationRepository annotations =
                new JdbcBuggyKnowledgeAnnotationRepository(transactions, contentPolicy);
        BuggyKnowledgeService buggy = new BuggyKnowledgeService(
                identity,
                catalog,
                knowledgeRepository,
                new KnowledgeAccessPolicy(),
                annotations,
                contentPolicy,
                Clock.fixed(NOW.plusSeconds(20), ZoneOffset.UTC));

        clearDedicatedTestState(transactions);
        try {
            identity.initialize(BuggyBootstrapSeed.create(OWNER, SYSTEM, NOW));
            KnowledgeDocument decision = buggy.recordOwnerDecision(new BuggyOwnerDecisionDraft(
                    "Economy rule",
                    BuggyKnowledgeTopic.HISTORICAL_DECISIONS_RATIONALE,
                    "No Coins; economy uses Vanilla Items.",
                    "network economy",
                    "Preserve Vanilla Minecraft trading semantics.",
                    "Network Owner",
                    "conversation:b4-postgres:decision",
                    "Owner decision",
                    BuggyKnowledgeVisibility.OWNER_CONFIDENTIAL,
                    "economy.medium",
                    "vanilla-items"), OWNER);
            publish(catalog, decision);

            require(buggy.search(search("Vanilla Items"), access(OWNER)).status() == KnowledgeSearchStatus.RESULTS,
                    "Owner Decision Memory retrieval");
            require(buggy.search(search("Vanilla Items"), access(OTHER)).status() == KnowledgeSearchStatus.NO_RELIABLE_RESULT,
                    "Owner Knowledge isolation");

            BuggyKnowledgeDraft candidate = new BuggyKnowledgeDraft(
                    BuggyKnowledgeArea.GENERAL,
                    BuggyKnowledgeTopic.HISTORICAL_DECISIONS_RATIONALE,
                    "Coin proposal",
                    BuggyKnowledgeVisibility.INTERNAL,
                    "Introduce Coins.",
                    Set.of(),
                    Map.of(),
                    BuggyKnowledgeProvenance.ASSISTANT_INFERENCE,
                    "proposal:b4-postgres:coins",
                    null,
                    BuggyKnowledgeConfidence.MEDIUM,
                    "economy.medium",
                    "coins");
            require(buggy.detectConflicts(candidate, access(OWNER)).status() == BuggyKnowledgeConflictStatus.CONFLICT,
                    "published Owner Decision conflict detected");

            var stale = buggy.markStale(
                    decision.documentId(),
                    "Superseding decision under review",
                    "decision-proposal:b4-postgres:2",
                    BuggyKnowledgeConfidence.HIGH,
                    OWNER);
            require(stale.state() == BuggyKnowledgeState.STALE, "stale annotation persisted");
            require(annotations.findCurrent(decision.documentId()).orElseThrow().revision() == 1,
                    "annotation head readable");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_knowledge_annotation_revisions SET reason='tampered' "
                            + "WHERE assistant_id='BUGGY' AND document_id='" + decision.documentId().value() + "'",
                    "annotation revision update rejected");

            expectFailure(() -> buggy.createDraft(new BuggyKnowledgeDraft(
                    BuggyKnowledgeArea.GENERAL,
                    BuggyKnowledgeTopic.DATABASES_MIGRATIONS,
                    "Credential leak",
                    BuggyKnowledgeVisibility.INTERNAL,
                    "password=must-not-persist",
                    Set.of(),
                    Map.of(),
                    BuggyKnowledgeProvenance.CONFIGURATION,
                    "config:b4-postgres",
                    null,
                    BuggyKnowledgeConfidence.HIGH,
                    null,
                    null), OWNER));
            System.out.println("B4_BUGGY_KNOWLEDGE_POSTGRES_SELF_TEST: PASS");
        } finally {
            clearDedicatedTestState(transactions);
        }
    }

    private static BuggyKnowledgeSearchRequest search(String text) {
        return new BuggyKnowledgeSearchRequest(text, Set.of(), Set.of(), Map.of(), false, false, 50);
    }

    private static BuggyKnowledgeAccess access(ActorId actorId) {
        return new BuggyKnowledgeAccess(actorId, true, READ_PERMISSIONS);
    }

    private static void publish(KnowledgeCatalogService catalog, KnowledgeDocument document) {
        KnowledgeDocument review = catalog.submitForReview(document.documentId(), document.currentRevision(), OWNER);
        catalog.publish(review.documentId(), review.currentRevision(), OWNER);
    }

    private static void clearDedicatedTestState(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "TRUNCATE TABLE "
                                + "assistant.buggy_knowledge_annotation_head, "
                                + "assistant.buggy_knowledge_annotation_revisions, "
                                + "knowledge.revisions, knowledge.documents, "
                                + "assistant.buggy_identity_head, assistant.buggy_config_revisions, "
                                + "assistant.buggy_owner_binding_revisions, assistant.buggy_identity_core CASCADE");
            }
            return null;
        });
    }

    private static void expectDatabaseFailure(
            JdbcTransactionRunner transactions,
            String sql,
            String description) {
        try {
            transactions.required(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
                return null;
            });
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(description);
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected operation failure");
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
            return Logger.getLogger("de.legitserver.assistant.integrations.buggy");
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
