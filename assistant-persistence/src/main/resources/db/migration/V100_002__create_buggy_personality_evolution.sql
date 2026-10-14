CREATE TABLE assistant.buggy_personality_seed (
    assistant_id VARCHAR(16) PRIMARY KEY REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    profile_id VARCHAR(96) NOT NULL CHECK (profile_id = 'buggy-personality-v1'),
    seed_version INTEGER NOT NULL CHECK (seed_version = 1),
    seed_sha256 CHAR(64) NOT NULL CHECK (seed_sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant.buggy_personality_seed_entries (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_personality_seed(assistant_id) ON DELETE RESTRICT,
    entry_ordinal INTEGER NOT NULL CHECK (entry_ordinal >= 0),
    domain VARCHAR(48) NOT NULL,
    attribute_key VARCHAR(96) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    PRIMARY KEY (assistant_id, entry_ordinal),
    UNIQUE (assistant_id, domain, attribute_key)
);

CREATE TABLE assistant.buggy_personality_observations (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_personality_seed(assistant_id) ON DELETE RESTRICT,
    observation_id VARCHAR(64) NOT NULL,
    domain VARCHAR(48) NOT NULL,
    attribute_key VARCHAR(96) NOT NULL,
    observed_value VARCHAR(512) NOT NULL,
    origin VARCHAR(48) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 1 AND 10000),
    note VARCHAR(512) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    observation_sha256 CHAR(64) NOT NULL CHECK (observation_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, observation_id)
);

CREATE TABLE assistant.buggy_personality_observation_evidence (
    assistant_id VARCHAR(16) NOT NULL,
    observation_id VARCHAR(64) NOT NULL,
    evidence_ordinal INTEGER NOT NULL CHECK (evidence_ordinal >= 0),
    evidence_type VARCHAR(48) NOT NULL,
    reference_id VARCHAR(160) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, observation_id, evidence_ordinal),
    FOREIGN KEY (assistant_id, observation_id)
        REFERENCES assistant.buggy_personality_observations(assistant_id, observation_id)
        ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_personality_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_personality_seed(assistant_id) ON DELETE RESTRICT,
    personality_revision BIGINT NOT NULL CHECK (personality_revision >= 1),
    previous_revision BIGINT NULL,
    origin VARCHAR(48) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    rollback_target_revision BIGINT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    state_sha256 CHAR(64) NOT NULL CHECK (state_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, personality_revision),
    CONSTRAINT ck_buggy_personality_revision_chain CHECK (
        (personality_revision = 1 AND previous_revision IS NULL)
        OR (personality_revision > 1 AND previous_revision = personality_revision - 1)
    ),
    CONSTRAINT ck_buggy_personality_rollback_target CHECK (
        (origin = 'ROLLBACK' AND rollback_target_revision IS NOT NULL AND rollback_target_revision < personality_revision)
        OR (origin <> 'ROLLBACK' AND rollback_target_revision IS NULL)
    ),
    CONSTRAINT fk_buggy_personality_previous_revision
        FOREIGN KEY (assistant_id, previous_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_buggy_personality_rollback_revision
        FOREIGN KEY (assistant_id, rollback_target_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_personality_revision_changes (
    assistant_id VARCHAR(16) NOT NULL,
    personality_revision BIGINT NOT NULL,
    change_ordinal INTEGER NOT NULL CHECK (change_ordinal >= 0),
    attribute_identity_key VARCHAR(160) NOT NULL,
    PRIMARY KEY (assistant_id, personality_revision, change_ordinal),
    UNIQUE (assistant_id, personality_revision, attribute_identity_key),
    FOREIGN KEY (assistant_id, personality_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_personality_revision_evidence (
    assistant_id VARCHAR(16) NOT NULL,
    personality_revision BIGINT NOT NULL,
    evidence_ordinal INTEGER NOT NULL CHECK (evidence_ordinal >= 0),
    observation_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (assistant_id, personality_revision, evidence_ordinal),
    UNIQUE (assistant_id, personality_revision, observation_id),
    FOREIGN KEY (assistant_id, personality_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        ON DELETE RESTRICT,
    FOREIGN KEY (assistant_id, observation_id)
        REFERENCES assistant.buggy_personality_observations(assistant_id, observation_id)
        ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_personality_state_entries (
    assistant_id VARCHAR(16) NOT NULL,
    personality_revision BIGINT NOT NULL,
    domain VARCHAR(48) NOT NULL,
    attribute_key VARCHAR(96) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    source_observation_id VARCHAR(64) NULL,
    PRIMARY KEY (assistant_id, personality_revision, domain, attribute_key),
    FOREIGN KEY (assistant_id, personality_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        ON DELETE RESTRICT,
    FOREIGN KEY (assistant_id, source_observation_id)
        REFERENCES assistant.buggy_personality_observations(assistant_id, observation_id)
        ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_personality_head (
    assistant_id VARCHAR(16) PRIMARY KEY REFERENCES assistant.buggy_personality_seed(assistant_id) ON DELETE RESTRICT,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (assistant_id, current_revision)
        REFERENCES assistant.buggy_personality_revisions(assistant_id, personality_revision)
        ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_personality_observation_attribute
    ON assistant.buggy_personality_observations(domain, attribute_key, created_at DESC);

CREATE INDEX idx_buggy_personality_revision_created
    ON assistant.buggy_personality_revisions(created_at DESC);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_personality_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy Personality seed/history is append-only';
END;
$$;

CREATE TRIGGER trg_buggy_personality_seed_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_seed
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_seed_entries_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_seed_entries
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_observations_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_observations
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_evidence_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_observation_evidence
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_revision_changes_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_revision_changes
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_revision_evidence_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_revision_evidence
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();

CREATE TRIGGER trg_buggy_personality_state_entries_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_personality_state_entries
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_personality_history_mutation();
