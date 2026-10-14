CREATE TABLE assistant.buggy_selfdev_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    item_id UUID NOT NULL,
    item_revision BIGINT NOT NULL CHECK (item_revision >= 1),
    previous_revision BIGINT NULL,
    state VARCHAR(40) NOT NULL CHECK (state IN (
        'OBSERVED','PROPOSAL','IMPLEMENTING','TESTING','REJECTED_BY_BUGGY',
        'WAITING_FOR_OWNER_APPROVAL','APPROVED','DEPLOYED','VERIFIED','ROLLED_BACK')),
    title VARCHAR(256) NOT NULL,
    problem TEXT NOT NULL CHECK (char_length(problem) BETWEEN 1 AND 8192),
    proposal_summary TEXT NULL CHECK (proposal_summary IS NULL OR char_length(proposal_summary) BETWEEN 1 AND 8192),
    change_type VARCHAR(32) NULL CHECK (change_type IS NULL OR change_type IN (
        'SOURCE_PATCH','CONFIGURATION_CHANGE','TOOL_CHANGE','WORKFLOW_CHANGE')),
    source_component VARCHAR(128) NOT NULL,
    source_version VARCHAR(160) NOT NULL,
    source_build VARCHAR(160) NOT NULL,
    source_revision VARCHAR(200) NOT NULL,
    source_created_at TIMESTAMPTZ NOT NULL,
    source_checksum CHAR(64) NOT NULL CHECK (source_checksum ~ '^[0-9a-f]{64}$'),
    workspace_ref VARCHAR(512) NULL,
    patch_ref VARCHAR(512) NULL,
    build_artifact_ref VARCHAR(512) NULL,
    risk_level VARCHAR(16) NULL CHECK (risk_level IS NULL OR risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    approval_request_id UUID NULL REFERENCES requests.records(request_id) ON DELETE RESTRICT,
    approval_submitted_item_revision BIGINT NULL CHECK (approval_submitted_item_revision IS NULL OR approval_submitted_item_revision >= 1),
    approval_request_type VARCHAR(128) NULL,
    approval_payload_sha256 CHAR(64) NULL CHECK (approval_payload_sha256 IS NULL OR approval_payload_sha256 ~ '^[0-9a-f]{64}$'),
    approval_risk_level VARCHAR(16) NULL CHECK (approval_risk_level IS NULL OR approval_risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    reason TEXT NULL CHECK (reason IS NULL OR char_length(reason) BETWEEN 1 AND 4096),
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, item_id, item_revision),
    CONSTRAINT ck_buggy_selfdev_revision_chain CHECK (
        (item_revision = 1 AND previous_revision IS NULL)
        OR (item_revision > 1 AND previous_revision = item_revision - 1)
    ),
    CONSTRAINT ck_buggy_selfdev_observed_shape CHECK (
        (state = 'OBSERVED' AND proposal_summary IS NULL AND change_type IS NULL AND risk_level IS NULL
            AND workspace_ref IS NULL AND patch_ref IS NULL AND build_artifact_ref IS NULL
            AND approval_request_id IS NULL)
        OR state <> 'OBSERVED'
    ),
    CONSTRAINT ck_buggy_selfdev_proposal_shape CHECK (
        state = 'OBSERVED' OR (proposal_summary IS NOT NULL AND change_type IS NOT NULL AND risk_level IS NOT NULL)
    ),
    CONSTRAINT ck_buggy_selfdev_no_low_discount CHECK (risk_level IS NULL OR risk_level <> 'LOW'),
    CONSTRAINT ck_buggy_selfdev_testing_patch CHECK (
        state NOT IN ('TESTING','WAITING_FOR_OWNER_APPROVAL','APPROVED','DEPLOYED','VERIFIED','ROLLED_BACK') OR patch_ref IS NOT NULL
    ),
    CONSTRAINT ck_buggy_selfdev_promotion_build CHECK (
        state NOT IN ('WAITING_FOR_OWNER_APPROVAL','APPROVED','DEPLOYED','VERIFIED','ROLLED_BACK') OR build_artifact_ref IS NOT NULL
    ),
    CONSTRAINT ck_buggy_selfdev_approval_binding CHECK (
        (state IN ('WAITING_FOR_OWNER_APPROVAL','APPROVED','DEPLOYED','VERIFIED','ROLLED_BACK')
            AND approval_request_id IS NOT NULL
            AND approval_submitted_item_revision IS NOT NULL
            AND approval_request_type IS NOT NULL
            AND approval_payload_sha256 IS NOT NULL
            AND approval_risk_level IS NOT NULL)
        OR
        (state NOT IN ('WAITING_FOR_OWNER_APPROVAL','APPROVED','DEPLOYED','VERIFIED','ROLLED_BACK')
            AND approval_request_id IS NULL
            AND approval_submitted_item_revision IS NULL
            AND approval_request_type IS NULL
            AND approval_payload_sha256 IS NULL
            AND approval_risk_level IS NULL)
    ),
    CONSTRAINT fk_buggy_selfdev_previous
        FOREIGN KEY (assistant_id, item_id, previous_revision)
        REFERENCES assistant.buggy_selfdev_revisions(assistant_id, item_id, item_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_selfdev_items (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    item_id UUID NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, item_id),
    CHECK (updated_at >= created_at),
    FOREIGN KEY (assistant_id, item_id, current_revision)
        REFERENCES assistant.buggy_selfdev_revisions(assistant_id, item_id, item_revision) ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_selfdev_items_updated
    ON assistant.buggy_selfdev_items(updated_at DESC, item_id);

CREATE INDEX idx_buggy_selfdev_revisions_state
    ON assistant.buggy_selfdev_revisions(state, changed_at DESC);

CREATE TABLE assistant.buggy_selfdev_evidence (
    evidence_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    item_id UUID NOT NULL,
    item_revision BIGINT NOT NULL CHECK (item_revision >= 1),
    evidence_type VARCHAR(40) NOT NULL CHECK (evidence_type IN (
        'WORKSPACE','PATCH','BUILD','TEST','SECURITY_SCAN','BENCHMARK','IMPACT_COMPARISON','SELF_REVIEW',
        'APPROVAL_REQUEST','DEPLOYMENT','POST_DEPLOY_VERIFICATION','ROLLBACK')),
    tool_id VARCHAR(128) NULL,
    reference_id VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    successful BOOLEAN NOT NULL,
    observed_by VARCHAR(128) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (assistant_id, item_id, item_revision)
        REFERENCES assistant.buggy_selfdev_revisions(assistant_id, item_id, item_revision) ON DELETE RESTRICT,
    CONSTRAINT ck_buggy_selfdev_evidence_tool CHECK (
        tool_id IS NOT NULL OR evidence_type IN ('SELF_REVIEW','APPROVAL_REQUEST','DEPLOYMENT','ROLLBACK'))
);

CREATE INDEX idx_buggy_selfdev_evidence_item
    ON assistant.buggy_selfdev_evidence(item_id, observed_at, evidence_id);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_b7_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy B7 Self-Development history is append-only';
END;
$$;

CREATE TRIGGER trg_buggy_selfdev_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_selfdev_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b7_history_mutation();

CREATE TRIGGER trg_buggy_selfdev_evidence_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_selfdev_evidence
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b7_history_mutation();
