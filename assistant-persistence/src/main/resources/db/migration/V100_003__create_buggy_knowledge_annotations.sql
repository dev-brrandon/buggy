CREATE TABLE assistant.buggy_knowledge_annotation_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    document_id UUID NOT NULL REFERENCES knowledge.documents(document_id) ON DELETE RESTRICT,
    annotation_revision BIGINT NOT NULL CHECK (annotation_revision >= 1),
    previous_revision BIGINT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('ACTIVE','STALE','DISPUTED')),
    reason VARCHAR(1024) NOT NULL,
    evidence_ref VARCHAR(1024) NOT NULL,
    confidence VARCHAR(16) NOT NULL CHECK (confidence IN ('VERIFIED','HIGH','MEDIUM','LOW')),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    annotation_sha256 CHAR(64) NOT NULL CHECK (annotation_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (assistant_id, document_id, annotation_revision),
    CONSTRAINT ck_buggy_knowledge_annotation_chain CHECK (
        (annotation_revision = 1 AND previous_revision IS NULL)
        OR (annotation_revision > 1 AND previous_revision = annotation_revision - 1)
    ),
    CONSTRAINT fk_buggy_knowledge_annotation_previous
        FOREIGN KEY (assistant_id, document_id, previous_revision)
        REFERENCES assistant.buggy_knowledge_annotation_revisions(assistant_id, document_id, annotation_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_knowledge_annotation_head (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    document_id UUID NOT NULL REFERENCES knowledge.documents(document_id) ON DELETE RESTRICT,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, document_id),
    FOREIGN KEY (assistant_id, document_id, current_revision)
        REFERENCES assistant.buggy_knowledge_annotation_revisions(assistant_id, document_id, annotation_revision)
        ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_knowledge_annotation_state
    ON assistant.buggy_knowledge_annotation_revisions(state, created_at DESC);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_knowledge_annotation_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy Knowledge annotation history is append-only';
END;
$$;

CREATE TRIGGER trg_buggy_knowledge_annotation_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_knowledge_annotation_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_knowledge_annotation_history_mutation();
