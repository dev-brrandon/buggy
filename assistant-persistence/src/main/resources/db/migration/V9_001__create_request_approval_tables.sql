CREATE TABLE requests.records (
    request_id UUID PRIMARY KEY,
    request_type VARCHAR(96) NOT NULL,
    requester_actor_id VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN (
        'PENDING','APPROVED','DENIED','EXPIRED','CANCELLED','EXECUTING','EXECUTED','FAILED')),
    risk_level VARCHAR(16) NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    required_approvals SMALLINT NOT NULL CHECK (required_approvals BETWEEN 1 AND 16),
    active_payload_revision BIGINT NOT NULL CHECK (active_payload_revision >= 1),
    active_payload_sha256 CHAR(64) NOT NULL CHECK (active_payload_sha256 ~ '^[0-9a-f]{64}$'),
    approved_payload_revision BIGINT NULL CHECK (approved_payload_revision IS NULL OR approved_payload_revision >= 1),
    approved_payload_sha256 CHAR(64) NULL CHECK (approved_payload_sha256 IS NULL OR approved_payload_sha256 ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NULL,
    classification VARCHAR(16) NOT NULL CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    CHECK (updated_at >= created_at),
    CHECK (expires_at IS NULL OR expires_at > created_at),
    CHECK (risk_level <> 'CRITICAL' OR expires_at IS NOT NULL),
    CHECK ((approved_payload_revision IS NULL) = (approved_payload_sha256 IS NULL)),
    CHECK (
        (state IN ('APPROVED','EXECUTING','EXECUTED','FAILED')
            AND approved_payload_revision = active_payload_revision
            AND approved_payload_sha256 = active_payload_sha256)
        OR
        (state IN ('PENDING','DENIED','EXPIRED','CANCELLED')
            AND approved_payload_revision IS NULL
            AND approved_payload_sha256 IS NULL)
    )
);

CREATE INDEX idx_request_records_state_risk_updated
    ON requests.records(state, risk_level, updated_at DESC);
CREATE INDEX idx_request_records_requester_updated
    ON requests.records(requester_actor_id, updated_at DESC);
CREATE INDEX idx_request_records_expiry
    ON requests.records(expires_at)
    WHERE expires_at IS NOT NULL AND state IN ('PENDING','APPROVED');

CREATE TABLE requests.payload_revisions (
    request_id UUID NOT NULL REFERENCES requests.records(request_id) ON DELETE RESTRICT,
    payload_revision BIGINT NOT NULL CHECK (payload_revision >= 1),
    content_type VARCHAR(128) NOT NULL,
    payload_body TEXT NOT NULL CHECK (octet_length(payload_body) <= 65536),
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    revised_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (request_id, payload_revision),
    UNIQUE (request_id, payload_revision, payload_sha256)
);

CREATE INDEX idx_request_payload_hash
    ON requests.payload_revisions(request_id, payload_sha256, payload_revision DESC);

CREATE TABLE requests.approvals (
    approval_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    payload_revision BIGINT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    approver_actor_id VARCHAR(128) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVE','DENY')),
    reason VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_request_approval_payload
        FOREIGN KEY (request_id, payload_revision, payload_sha256)
        REFERENCES requests.payload_revisions(request_id, payload_revision, payload_sha256)
        ON DELETE RESTRICT,
    UNIQUE (request_id, payload_revision, approver_actor_id)
);

CREATE INDEX idx_request_approvals_active_count
    ON requests.approvals(request_id, payload_revision, payload_sha256, decision);

CREATE TABLE requests.history (
    history_seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    history_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN (
        'CREATED','PAYLOAD_REVISED','APPROVAL_RECORDED','DENIAL_RECORDED',
        'EXPIRED','CANCELLED','EXECUTION_STARTED','EXECUTED','FAILED')),
    actor_id VARCHAR(128) NOT NULL,
    payload_revision BIGINT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    before_state VARCHAR(16) NULL CHECK (before_state IS NULL OR before_state IN (
        'PENDING','APPROVED','DENIED','EXPIRED','CANCELLED','EXECUTING','EXECUTED','FAILED')),
    after_state VARCHAR(16) NOT NULL CHECK (after_state IN (
        'PENDING','APPROVED','DENIED','EXPIRED','CANCELLED','EXECUTING','EXECUTED','FAILED')),
    details JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(details) = 'object')
        CHECK (octet_length(details::text) <= 16384),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_request_history_payload
        FOREIGN KEY (request_id, payload_revision, payload_sha256)
        REFERENCES requests.payload_revisions(request_id, payload_revision, payload_sha256)
        ON DELETE RESTRICT
);

CREATE INDEX idx_request_history_request_sequence
    ON requests.history(request_id, history_seq ASC);

CREATE OR REPLACE FUNCTION requests.reject_request_provenance_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.request_id IS DISTINCT FROM OLD.request_id
       OR NEW.request_type IS DISTINCT FROM OLD.request_type
       OR NEW.requester_actor_id IS DISTINCT FROM OLD.requester_actor_id
       OR NEW.risk_level IS DISTINCT FROM OLD.risk_level
       OR NEW.required_approvals IS DISTINCT FROM OLD.required_approvals
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.classification IS DISTINCT FROM OLD.classification
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Request creation policy/provenance is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_request_provenance_immutable
BEFORE UPDATE ON requests.records
FOR EACH ROW
EXECUTE FUNCTION requests.reject_request_provenance_mutation();

CREATE OR REPLACE FUNCTION requests.reject_append_only_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Request payload revisions, approvals, and history are append-only';
END;
$$;

CREATE TRIGGER trg_request_payload_append_only
BEFORE UPDATE OR DELETE ON requests.payload_revisions
FOR EACH ROW
EXECUTE FUNCTION requests.reject_append_only_mutation();

CREATE TRIGGER trg_request_approval_append_only
BEFORE UPDATE OR DELETE ON requests.approvals
FOR EACH ROW
EXECUTE FUNCTION requests.reject_append_only_mutation();

CREATE TRIGGER trg_request_history_append_only
BEFORE UPDATE OR DELETE ON requests.history
FOR EACH ROW
EXECUTE FUNCTION requests.reject_append_only_mutation();
