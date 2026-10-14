CREATE TABLE assistant.buggy_native_core_seed (
    assistant_id VARCHAR(16) PRIMARY KEY REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    concept_date DATE NOT NULL,
    birth_at TIMESTAMPTZ NOT NULL,
    birth_type VARCHAR(48) NOT NULL CHECK (birth_type = 'NATIVE_CORE_ACTIVATION'),
    initial_stage VARCHAR(32) NOT NULL CHECK (initial_stage = 'BOOTSTRAP'),
    protocol_catalog_version INTEGER NOT NULL CHECK (protocol_catalog_version = 1),
    protocol_catalog_sha256 CHAR(64) NOT NULL CHECK (protocol_catalog_sha256 ~ '^[0-9a-f]{64}$'),
    personality_schema_version INTEGER NOT NULL CHECK (personality_schema_version = 1),
    personality_baseline_sha256 CHAR(64) NOT NULL CHECK (personality_baseline_sha256 ~ '^[0-9a-f]{64}$'),
    state_sha256 CHAR(64) NOT NULL CHECK (state_sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant.buggy_native_core_protocols (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_native_core_seed(assistant_id) ON DELETE RESTRICT,
    protocol_ordinal INTEGER NOT NULL CHECK (protocol_ordinal >= 0),
    protocol_code VARCHAR(48) NOT NULL,
    protocol_id VARCHAR(96) NOT NULL,
    protocol_version INTEGER NOT NULL CHECK (protocol_version = 1),
    summary VARCHAR(640) NOT NULL,
    protocol_sha256 CHAR(64) NOT NULL CHECK (protocol_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, protocol_ordinal),
    UNIQUE (assistant_id, protocol_code),
    UNIQUE (assistant_id, protocol_id)
);

CREATE TABLE assistant.buggy_native_personality_baseline (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_native_core_seed(assistant_id) ON DELETE RESTRICT,
    axis VARCHAR(48) NOT NULL,
    start_bps INTEGER NOT NULL CHECK (start_bps BETWEEN 0 AND 10000),
    minimum_bps INTEGER NOT NULL CHECK (minimum_bps BETWEEN 0 AND 10000),
    maximum_bps INTEGER NOT NULL CHECK (maximum_bps BETWEEN 0 AND 10000),
    PRIMARY KEY (assistant_id, axis),
    CHECK (minimum_bps <= start_bps AND start_bps <= maximum_bps)
);

CREATE TABLE assistant.buggy_native_maturity_baseline (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_native_core_seed(assistant_id) ON DELETE RESTRICT,
    dimension VARCHAR(48) NOT NULL,
    score_bps INTEGER NOT NULL CHECK (score_bps BETWEEN 0 AND 10000),
    PRIMARY KEY (assistant_id, dimension)
);

CREATE TABLE assistant.buggy_native_self_capabilities (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_native_core_seed(assistant_id) ON DELETE RESTRICT,
    capability_id VARCHAR(96) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('AVAILABLE', 'LIMITED', 'UNAVAILABLE')),
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    limitation VARCHAR(320) NULL,
    PRIMARY KEY (assistant_id, capability_id),
    CHECK (status <> 'UNAVAILABLE' OR confidence_bps = 0)
);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_native_core_baseline_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy Native-Core birth/baseline is immutable';
END;
$$;

CREATE TRIGGER trg_buggy_native_core_seed_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_core_seed
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_native_core_baseline_mutation();

CREATE TRIGGER trg_buggy_native_core_protocols_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_core_protocols
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_native_core_baseline_mutation();

CREATE TRIGGER trg_buggy_native_personality_baseline_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_personality_baseline
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_native_core_baseline_mutation();

CREATE TRIGGER trg_buggy_native_maturity_baseline_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_maturity_baseline
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_native_core_baseline_mutation();

CREATE TRIGGER trg_buggy_native_self_capabilities_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_self_capabilities
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_native_core_baseline_mutation();
