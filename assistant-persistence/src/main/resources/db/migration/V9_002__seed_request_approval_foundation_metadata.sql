INSERT INTO assistant.platform_metadata(metadata_key, metadata_value)
VALUES ('foundation.requests.version', 'phase9')
ON CONFLICT (metadata_key) DO UPDATE
SET metadata_value = EXCLUDED.metadata_value,
    revision = assistant.platform_metadata.revision + 1,
    updated_at = CURRENT_TIMESTAMP;
