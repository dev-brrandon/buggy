CREATE TABLE knowledge.documents (
    document_id UUID PRIMARY KEY,
    domain VARCHAR(48) NOT NULL,
    title VARCHAR(256) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_revision BIGINT NOT NULL,
    valid_from TIMESTAMP WITH TIME ZONE NULL,
    required_permissions TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title_search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', title)) STORED,
    CONSTRAINT ck_knowledge_document_domain CHECK (domain IN (
        'MODERATION','STAFF_PROCEDURES','BUG_REPORTING','SERVER_OPERATIONS',
        'MINECRAFT_ARCHITECTURE','PANEL','DEPLOYMENTS','TROUBLESHOOTING','SECURITY')),
    CONSTRAINT ck_knowledge_document_classification CHECK (
        classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
    CONSTRAINT ck_knowledge_document_status CHECK (status IN ('DRAFT','REVIEW','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_knowledge_document_revision CHECK (current_revision >= 1),
    CONSTRAINT ck_knowledge_document_title CHECK (octet_length(title) BETWEEN 1 AND 1024),
    CONSTRAINT ck_knowledge_document_time_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_knowledge_document_publication CHECK (
        (status = 'PUBLISHED' AND valid_from IS NOT NULL AND valid_from <= updated_at)
        OR (status IN ('DRAFT','REVIEW') AND valid_from IS NULL)
        OR status = 'ARCHIVED'),
    CONSTRAINT ck_knowledge_document_acl_count CHECK (cardinality(required_permissions) <= 64)
);

CREATE INDEX ix_knowledge_documents_status_domain_updated
    ON knowledge.documents (status, domain, updated_at DESC);
CREATE INDEX ix_knowledge_documents_classification
    ON knowledge.documents (classification);
CREATE INDEX ix_knowledge_documents_title_fts
    ON knowledge.documents USING GIN (title_search_vector);
CREATE INDEX ix_knowledge_documents_acl
    ON knowledge.documents USING GIN (required_permissions);

CREATE TABLE knowledge.revisions (
    document_id UUID NOT NULL REFERENCES knowledge.documents(document_id),
    revision BIGINT NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    source_kind VARCHAR(64) NOT NULL,
    source_ref VARCHAR(1024) NOT NULL,
    source_label VARCHAR(256) NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    PRIMARY KEY (document_id, revision),
    CONSTRAINT ck_knowledge_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_knowledge_revision_content_size CHECK (octet_length(content) BETWEEN 1 AND 1048576),
    CONSTRAINT ck_knowledge_revision_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_knowledge_revision_tag_count CHECK (cardinality(tags) <= 64),
    CONSTRAINT ck_knowledge_revision_metadata_object CHECK (jsonb_typeof(metadata_json) = 'object'),
    CONSTRAINT ck_knowledge_revision_metadata_size CHECK (octet_length(metadata_json::text) <= 16384)
);

CREATE INDEX ix_knowledge_revisions_fts
    ON knowledge.revisions USING GIN (search_vector);
CREATE INDEX ix_knowledge_revisions_tags
    ON knowledge.revisions USING GIN (tags);
CREATE INDEX ix_knowledge_revisions_metadata
    ON knowledge.revisions USING GIN (metadata_json jsonb_path_ops);

CREATE FUNCTION knowledge.reject_revision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Knowledge revisions are immutable';
END;
$$;

CREATE TRIGGER trg_knowledge_revisions_immutable
BEFORE UPDATE OR DELETE ON knowledge.revisions
FOR EACH ROW
EXECUTE FUNCTION knowledge.reject_revision_mutation();
