INSERT INTO assistant.platform_metadata (metadata_key, metadata_value)
VALUES ('foundation.persistence.version', 'phase4')
ON CONFLICT (metadata_key) DO NOTHING;
