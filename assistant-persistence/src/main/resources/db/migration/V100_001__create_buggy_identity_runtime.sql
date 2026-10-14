CREATE TABLE assistant.buggy_identity_core (
    assistant_id VARCHAR(16) PRIMARY KEY CHECK (assistant_id = 'BUGGY'),
    canonical_id VARCHAR(32) NOT NULL CHECK (canonical_id = 'buggy'),
    display_name VARCHAR(80) NOT NULL CHECK (display_name = 'Buggy'),
    service_identity VARCHAR(96) NOT NULL CHECK (service_identity = 'assistant:buggy'),
    assistant_type VARCHAR(64) NOT NULL CHECK (assistant_type = 'OWNER_ASSISTANT'),
    primary_role VARCHAR(96) NOT NULL CHECK (primary_role = 'PERSONAL_OWNER_AND_NETWORK_OPERATIONS_ASSISTANT'),
    authority_tier VARCHAR(64) NOT NULL CHECK (authority_tier = 'HIGHEST_ASSISTANT_TIER'),
    security_authority VARCHAR(64) NOT NULL CHECK (security_authority = 'NETWORKCORE'),
    core_version BIGINT NOT NULL CHECK (core_version = 1),
    core_sha256 CHAR(64) NOT NULL CHECK (core_sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant.buggy_owner_binding_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    binding_revision BIGINT NOT NULL CHECK (binding_revision >= 1),
    owner_actor_id VARCHAR(128) NOT NULL,
    authority_source VARCHAR(64) NOT NULL CHECK (authority_source = 'NETWORKCORE'),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    binding_sha256 CHAR(64) NOT NULL CHECK (binding_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, binding_revision)
);

CREATE TABLE assistant.buggy_config_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    config_revision BIGINT NOT NULL CHECK (config_revision >= 1),
    manifest_schema_version INTEGER NOT NULL CHECK (manifest_schema_version = 1),
    display_name VARCHAR(80) NOT NULL,
    capability_profile_id VARCHAR(96) NOT NULL,
    policy_profile_id VARCHAR(96) NOT NULL,
    knowledge_profile_id VARCHAR(96) NOT NULL,
    runtime_profile_id VARCHAR(96) NOT NULL,
    personality_profile_id VARCHAR(96) NULL,
    persistent_identity BOOLEAN NOT NULL CHECK (persistent_identity),
    owner_directed BOOLEAN NOT NULL CHECK (owner_directed),
    network_wide BOOLEAN NOT NULL CHECK (network_wide),
    self_approval_allowed BOOLEAN NOT NULL CHECK (NOT self_approval_allowed),
    authority_self_escalation_allowed BOOLEAN NOT NULL CHECK (NOT authority_self_escalation_allowed),
    risk_downgrade_allowed BOOLEAN NOT NULL CHECK (NOT risk_downgrade_allowed),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    config_sha256 CHAR(64) NOT NULL CHECK (config_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, config_revision)
);

CREATE TABLE assistant.buggy_identity_head (
    assistant_id VARCHAR(16) PRIMARY KEY REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    active_owner_binding_revision BIGINT NOT NULL CHECK (active_owner_binding_revision >= 1),
    active_config_revision BIGINT NOT NULL CHECK (active_config_revision >= 1),
    head_revision BIGINT NOT NULL CHECK (head_revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_buggy_active_owner_binding
        FOREIGN KEY (assistant_id, active_owner_binding_revision)
        REFERENCES assistant.buggy_owner_binding_revisions(assistant_id, binding_revision)
        ON DELETE RESTRICT,
    CONSTRAINT fk_buggy_active_config
        FOREIGN KEY (assistant_id, active_config_revision)
        REFERENCES assistant.buggy_config_revisions(assistant_id, config_revision)
        ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_owner_binding_owner
    ON assistant.buggy_owner_binding_revisions(owner_actor_id, binding_revision DESC);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_identity_core_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy Identity Core is protected and immutable';
END;
$$;

CREATE TRIGGER trg_buggy_identity_core_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_identity_core
FOR EACH ROW
EXECUTE FUNCTION assistant.reject_buggy_identity_core_mutation();

CREATE OR REPLACE FUNCTION assistant.reject_buggy_identity_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy owner/config revisions are append-only';
END;
$$;

CREATE TRIGGER trg_buggy_owner_binding_append_only
BEFORE UPDATE OR DELETE ON assistant.buggy_owner_binding_revisions
FOR EACH ROW
EXECUTE FUNCTION assistant.reject_buggy_identity_history_mutation();

CREATE TRIGGER trg_buggy_config_revision_append_only
BEFORE UPDATE OR DELETE ON assistant.buggy_config_revisions
FOR EACH ROW
EXECUTE FUNCTION assistant.reject_buggy_identity_history_mutation();
