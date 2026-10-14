CREATE TABLE bugs.records (
    internal_bug_id UUID PRIMARY KEY,
    public_bug_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'OPEN','TRIAGED','INVESTIGATING','FIX_PLANNED','FIX_IN_PROGRESS',
        'FIXED','VERIFIED','CLOSED','REJECTED','DUPLICATE')),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    priority VARCHAR(8) NOT NULL CHECK (priority IN ('P4','P3','P2','P1','P0')),
    reporter_actor_id VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    original_report TEXT NOT NULL CHECK (octet_length(original_report) <= 262144),
    original_report_sha256 CHAR(64) NOT NULL CHECK (original_report_sha256 ~ '^[0-9a-f]{64}$'),
    assistant_summary TEXT NULL CHECK (assistant_summary IS NULL OR octet_length(assistant_summary) <= 32768),
    component_id VARCHAR(128) NULL,
    plugin_version VARCHAR(128) NULL,
    build_version VARCHAR(128) NULL,
    server_id VARCHAR(128) NULL,
    environment VARCHAR(64) NULL,
    classification VARCHAR(16) NOT NULL CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    structured_metadata JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(structured_metadata) = 'object')
        CHECK (octet_length(structured_metadata::text) <= 16384)
);

CREATE INDEX idx_bug_records_status_updated ON bugs.records(status, updated_at DESC);
CREATE INDEX idx_bug_records_severity_priority ON bugs.records(severity, priority, updated_at DESC);
CREATE INDEX idx_bug_records_component_version ON bugs.records(component_id, plugin_version, build_version);
CREATE INDEX idx_bug_records_server_environment ON bugs.records(server_id, environment);

CREATE TABLE bugs.history (
    history_seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    history_id UUID PRIMARY KEY,
    internal_bug_id UUID NOT NULL REFERENCES bugs.records(internal_bug_id) ON DELETE RESTRICT,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN (
        'CREATED','STATUS_CHANGED','SEVERITY_CHANGED','PRIORITY_CHANGED','SUMMARY_CHANGED',
        'TARGET_MAPPING_CHANGED','METADATA_CHANGED','RELATION_ADDED','RELATION_REMOVED','INCIDENT_LINKED')),
    actor_id VARCHAR(128) NOT NULL,
    before_json JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(before_json) = 'object')
        CHECK (octet_length(before_json::text) <= 16384),
    after_json JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(after_json) = 'object')
        CHECK (octet_length(after_json::text) <= 16384),
    reason VARCHAR(1024) NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bug_history_bug_sequence ON bugs.history(internal_bug_id, history_seq DESC);

CREATE TABLE bugs.relations (
    source_bug_id UUID NOT NULL REFERENCES bugs.records(internal_bug_id) ON DELETE RESTRICT,
    relation_type VARCHAR(32) NOT NULL CHECK (relation_type IN (
        'DUPLICATE_OF','BLOCKS','BLOCKED_BY','RELATED_TO','REGRESSION_OF')),
    target_bug_id UUID NOT NULL REFERENCES bugs.records(internal_bug_id) ON DELETE RESTRICT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(source_bug_id, relation_type, target_bug_id),
    CHECK (source_bug_id <> target_bug_id)
);

CREATE INDEX idx_bug_relations_target ON bugs.relations(target_bug_id, relation_type);

CREATE TABLE bugs.incident_links (
    internal_bug_id UUID NOT NULL REFERENCES bugs.records(internal_bug_id) ON DELETE RESTRICT,
    internal_incident_id VARCHAR(128) NOT NULL,
    public_error_id VARCHAR(128) NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(internal_bug_id, internal_incident_id)
);

CREATE INDEX idx_bug_incident_links_incident ON bugs.incident_links(internal_incident_id);
CREATE INDEX idx_bug_incident_links_public_error ON bugs.incident_links(public_error_id) WHERE public_error_id IS NOT NULL;

CREATE OR REPLACE FUNCTION bugs.reject_original_report_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.internal_bug_id IS DISTINCT FROM OLD.internal_bug_id
       OR NEW.public_bug_id IS DISTINCT FROM OLD.public_bug_id
       OR NEW.reporter_actor_id IS DISTINCT FROM OLD.reporter_actor_id
       OR NEW.source IS DISTINCT FROM OLD.source
       OR NEW.original_report IS DISTINCT FROM OLD.original_report
       OR NEW.original_report_sha256 IS DISTINCT FROM OLD.original_report_sha256
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Bug original report and creation provenance are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_bug_original_report_immutable
BEFORE UPDATE ON bugs.records
FOR EACH ROW
EXECUTE FUNCTION bugs.reject_original_report_mutation();

CREATE OR REPLACE FUNCTION bugs.reject_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Bug history is append-only';
END;
$$;

CREATE TRIGGER trg_bug_history_append_only
BEFORE UPDATE OR DELETE ON bugs.history
FOR EACH ROW
EXECUTE FUNCTION bugs.reject_history_mutation();
