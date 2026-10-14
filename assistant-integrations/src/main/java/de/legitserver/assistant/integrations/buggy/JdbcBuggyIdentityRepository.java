package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.config.BuggyManifestCanonicalizer;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.identity.BuggyOwnerBinding;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL adapter for the B1/B2 Buggy persistent identity aggregate. */
public final class JdbcBuggyIdentityRepository implements BuggyIdentityRepository {
    private static final long PROVISIONING_ADVISORY_LOCK = 0x4255474759423031L;
    private static final String LOAD_SQL = """
            SELECT
                c.assistant_id,
                c.canonical_id,
                c.display_name AS core_display_name,
                c.service_identity,
                c.assistant_type,
                c.primary_role,
                c.authority_tier,
                c.security_authority,
                c.core_version,
                c.core_sha256,
                c.created_at AS core_created_at,
                o.binding_revision,
                o.owner_actor_id,
                o.authority_source,
                o.created_by AS owner_created_by,
                o.created_at AS owner_created_at,
                o.binding_sha256,
                cfg.config_revision,
                cfg.manifest_schema_version,
                cfg.display_name AS config_display_name,
                cfg.capability_profile_id,
                cfg.policy_profile_id,
                cfg.knowledge_profile_id,
                cfg.runtime_profile_id,
                cfg.personality_profile_id,
                cfg.persistent_identity,
                cfg.owner_directed,
                cfg.network_wide,
                cfg.self_approval_allowed,
                cfg.authority_self_escalation_allowed,
                cfg.risk_downgrade_allowed,
                cfg.created_by AS config_created_by,
                cfg.created_at AS config_created_at,
                cfg.config_sha256,
                h.head_revision,
                h.updated_at
            FROM assistant.buggy_identity_head h
            JOIN assistant.buggy_identity_core c ON c.assistant_id = h.assistant_id
            JOIN assistant.buggy_owner_binding_revisions o
              ON o.assistant_id = h.assistant_id
             AND o.binding_revision = h.active_owner_binding_revision
            JOIN assistant.buggy_config_revisions cfg
              ON cfg.assistant_id = h.assistant_id
             AND cfg.config_revision = h.active_config_revision
            WHERE h.assistant_id = 'BUGGY'
            """;

    private final TransactionRunner transactions;

    public JdbcBuggyIdentityRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Optional<BuggyIdentitySnapshot> find() {
        return transactions.readOnly(JdbcBuggyIdentityRepository::find);
    }

    @Override
    public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
        Objects.requireNonNull(seed, "seed");
        return transactions.required(connection -> {
            lockProvisioning(connection);
            Optional<BuggyIdentitySnapshot> existing = find(connection);
            if (existing.isPresent()) {
                requireEquivalent(existing.orElseThrow(), seed);
                return existing.orElseThrow();
            }
            insertCore(connection, seed.identityCore());
            insertOwnerBinding(connection, seed.ownerBinding());
            insertConfiguration(connection, seed.configuration());
            insertHead(connection, seed);
            return find(connection).orElseThrow(() -> new IllegalStateException("Buggy identity insert was not readable"));
        });
    }

    @Override
    public BuggyIdentitySnapshot activatePersonalityProfile(
            String profileId, ActorId createdBy, Instant createdAt) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        return transactions.required(connection -> {
            lockProvisioning(connection);
            BuggyIdentitySnapshot current = find(connection)
                    .orElseThrow(() -> new IllegalStateException("Buggy identity must be provisioned before Personality"));
            String activeProfile = current.configuration().manifest().personalityProfileId();
            if (profileId.equals(activeProfile)) return current;
            if (activeProfile != null) {
                throw new IllegalStateException("Buggy identity already references a different personality profile");
            }

            BuggyManifest manifest = current.configuration().manifest().withPersonalityProfileId(profileId);
            BuggyConfigurationRevision next = new BuggyConfigurationRevision(
                    current.configuration().revision() + 1,
                    manifest,
                    createdBy,
                    createdAt,
                    BuggyManifestCanonicalizer.sha256(manifest));
            insertConfiguration(connection, next);
            updateHeadConfiguration(connection, current, next);
            return find(connection).orElseThrow(() -> new IllegalStateException("Buggy personality profile activation was not readable"));
        });
    }

    private static void lockProvisioning(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, PROVISIONING_ADVISORY_LOCK);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Buggy provisioning advisory lock returned no row");
            }
        }
    }

    private static Optional<BuggyIdentitySnapshot> find(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_SQL);
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            BuggyIdentitySnapshot result = map(rs);
            if (rs.next()) throw new IllegalStateException("Multiple active Buggy identity heads found");
            return Optional.of(result);
        }
    }

    private static BuggyIdentitySnapshot map(ResultSet rs) throws SQLException {
        AssistantId assistantId = AssistantId.valueOf(rs.getString("assistant_id"));
        BuggyIdentityCore core = new BuggyIdentityCore(
                assistantId,
                rs.getString("canonical_id"),
                rs.getString("core_display_name"),
                rs.getString("service_identity"),
                rs.getString("assistant_type"),
                rs.getString("primary_role"),
                rs.getString("authority_tier"),
                rs.getString("security_authority"),
                BuggyIdentityCore.SCOPES,
                BuggyIdentityCore.PRINCIPLES,
                rs.getLong("core_version"),
                rs.getString("core_sha256"),
                rs.getTimestamp("core_created_at").toInstant());

        BuggyOwnerBinding owner = new BuggyOwnerBinding(
                rs.getLong("binding_revision"),
                new ActorId(rs.getString("owner_actor_id")),
                rs.getString("authority_source"),
                new ActorId(rs.getString("owner_created_by")),
                rs.getTimestamp("owner_created_at").toInstant(),
                rs.getString("binding_sha256"));

        BuggyManifest manifest = new BuggyManifest(
                rs.getInt("manifest_schema_version"),
                assistantId,
                BuggyIdentityCore.CANONICAL_ID,
                rs.getString("config_display_name"),
                BuggyIdentityCore.SERVICE_IDENTITY,
                BuggyIdentityCore.ASSISTANT_TYPE,
                BuggyIdentityCore.SCOPES,
                BuggyIdentityCore.PRIMARY_ROLE,
                BuggyIdentityCore.AUTHORITY_TIER,
                BuggyIdentityCore.SECURITY_AUTHORITY,
                rs.getString("capability_profile_id"),
                rs.getString("policy_profile_id"),
                rs.getString("knowledge_profile_id"),
                rs.getString("runtime_profile_id"),
                rs.getString("personality_profile_id"),
                rs.getBoolean("persistent_identity"),
                rs.getBoolean("owner_directed"),
                rs.getBoolean("network_wide"),
                rs.getBoolean("self_approval_allowed"),
                rs.getBoolean("authority_self_escalation_allowed"),
                rs.getBoolean("risk_downgrade_allowed"));

        BuggyConfigurationRevision configuration = new BuggyConfigurationRevision(
                rs.getLong("config_revision"),
                manifest,
                new ActorId(rs.getString("config_created_by")),
                rs.getTimestamp("config_created_at").toInstant(),
                rs.getString("config_sha256"));

        return new BuggyIdentitySnapshot(
                core,
                owner,
                configuration,
                rs.getLong("head_revision"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static void insertCore(Connection connection, BuggyIdentityCore core) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_identity_core(assistant_id, canonical_id, display_name, service_identity, assistant_type, primary_role, authority_tier, security_authority, core_version, core_sha256, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, core.assistantId().name());
            statement.setString(2, core.canonicalId());
            statement.setString(3, core.displayName());
            statement.setString(4, core.serviceIdentity());
            statement.setString(5, core.assistantType());
            statement.setString(6, core.primaryRole());
            statement.setString(7, core.authorityTier());
            statement.setString(8, core.securityAuthority());
            statement.setLong(9, core.coreVersion());
            statement.setString(10, core.sha256());
            statement.setTimestamp(11, Timestamp.from(core.createdAt()));
            requireOne(statement.executeUpdate(), "Buggy identity core");
        }
    }

    private static void insertOwnerBinding(Connection connection, BuggyOwnerBinding owner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_owner_binding_revisions(assistant_id, binding_revision, owner_actor_id, authority_source, created_by, created_at, binding_sha256) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, owner.revision());
            statement.setString(2, owner.ownerActorId().value());
            statement.setString(3, owner.authoritySource());
            statement.setString(4, owner.createdBy().value());
            statement.setTimestamp(5, Timestamp.from(owner.createdAt()));
            statement.setString(6, owner.sha256());
            requireOne(statement.executeUpdate(), "Buggy owner binding");
        }
    }

    private static void insertConfiguration(Connection connection, BuggyConfigurationRevision revision) throws SQLException {
        BuggyManifest manifest = revision.manifest();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_config_revisions(assistant_id, config_revision, manifest_schema_version, display_name, capability_profile_id, policy_profile_id, knowledge_profile_id, runtime_profile_id, personality_profile_id, persistent_identity, owner_directed, network_wide, self_approval_allowed, authority_self_escalation_allowed, risk_downgrade_allowed, created_by, created_at, config_sha256) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int index = 1;
            statement.setLong(index++, revision.revision());
            statement.setInt(index++, manifest.schemaVersion());
            statement.setString(index++, manifest.displayName());
            statement.setString(index++, manifest.capabilityProfileId());
            statement.setString(index++, manifest.policyProfileId());
            statement.setString(index++, manifest.knowledgeProfileId());
            statement.setString(index++, manifest.runtimeProfileId());
            statement.setString(index++, manifest.personalityProfileId());
            statement.setBoolean(index++, manifest.persistentIdentity());
            statement.setBoolean(index++, manifest.ownerDirected());
            statement.setBoolean(index++, manifest.networkWide());
            statement.setBoolean(index++, manifest.selfApprovalAllowed());
            statement.setBoolean(index++, manifest.authoritySelfEscalationAllowed());
            statement.setBoolean(index++, manifest.riskDowngradeAllowed());
            statement.setString(index++, revision.createdBy().value());
            statement.setTimestamp(index++, Timestamp.from(revision.createdAt()));
            statement.setString(index, revision.sha256());
            requireOne(statement.executeUpdate(), "Buggy configuration revision");
        }
    }

    private static void insertHead(Connection connection, BuggyBootstrapSeed seed) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_identity_head(assistant_id, active_owner_binding_revision, active_config_revision, head_revision, updated_at) "
                        + "VALUES ('BUGGY', ?, ?, 1, ?)")) {
            statement.setLong(1, seed.ownerBinding().revision());
            statement.setLong(2, seed.configuration().revision());
            statement.setTimestamp(3, Timestamp.from(seed.configuration().createdAt()));
            requireOne(statement.executeUpdate(), "Buggy identity head");
        }
    }

    private static void updateHeadConfiguration(
            Connection connection, BuggyIdentitySnapshot current, BuggyConfigurationRevision next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_identity_head "
                        + "SET active_config_revision=?, head_revision=head_revision+1, updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND head_revision=? AND active_config_revision=?")) {
            statement.setLong(1, next.revision());
            statement.setTimestamp(2, Timestamp.from(next.createdAt()));
            statement.setLong(3, current.headRevision());
            statement.setLong(4, current.configuration().revision());
            requireOne(statement.executeUpdate(), "Buggy identity head personality activation");
        }
    }

    private static void requireEquivalent(BuggyIdentitySnapshot existing, BuggyBootstrapSeed seed) {
        boolean sameProtectedIdentity = existing.identityCore().sha256().equals(seed.identityCore().sha256())
                && existing.ownerBinding().sha256().equals(seed.ownerBinding().sha256());
        if (!sameProtectedIdentity) {
            throw new IllegalStateException("Buggy identity is already provisioned with different protected B1 state");
        }
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected row count != 1");
    }
}
