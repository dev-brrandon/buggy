CREATE TABLE assistant.platform_metadata (
    metadata_key VARCHAR(128) PRIMARY KEY,
    metadata_value TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT ck_platform_metadata_revision_non_negative CHECK (revision >= 0)
);

CREATE INDEX ix_platform_metadata_active
    ON assistant.platform_metadata (metadata_key)
    WHERE deleted_at IS NULL;
