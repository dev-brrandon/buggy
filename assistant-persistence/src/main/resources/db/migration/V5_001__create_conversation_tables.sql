CREATE TABLE assistant.conversations (
    conversation_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL,
    owner_actor_id VARCHAR(128) NOT NULL,
    origin VARCHAR(16) NOT NULL,
    context_type VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_conversations_assistant CHECK (assistant_id IN ('KAESE','BYTE','BUGGY')),
    CONSTRAINT ck_conversations_origin CHECK (origin IN ('PANEL','MINECRAFT','DISCORD','INTERNAL','SYSTEM')),
    CONSTRAINT ck_conversations_context CHECK (context_type IN ('GENERAL','BUG_INTAKE','BUG_ANALYSIS','KNOWLEDGE','DIAGNOSTICS','REQUEST')),
    CONSTRAINT ck_conversations_state CHECK (state IN ('CREATED','ACTIVE','WAITING_FOR_INPUT','WAITING_FOR_APPROVAL','PROCESSING','COMPLETED','CANCELLED','EXPIRED','FAILED')),
    CONSTRAINT ck_conversations_classification CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
    CONSTRAINT ck_conversations_revision CHECK (revision >= 0),
    CONSTRAINT ck_conversations_time_order CHECK (updated_at >= created_at AND last_activity_at >= created_at),
    CONSTRAINT ck_conversations_expiry CHECK (expires_at IS NULL OR expires_at >= last_activity_at),
    CONSTRAINT ck_conversations_metadata_object CHECK (jsonb_typeof(metadata_json) = 'object'),
    CONSTRAINT ck_conversations_metadata_size CHECK (octet_length(metadata_json::text) <= 16384)
);

CREATE INDEX ix_conversations_owner_updated
    ON assistant.conversations (owner_actor_id, updated_at DESC);
CREATE INDEX ix_conversations_assistant_state_updated
    ON assistant.conversations (assistant_id, state, updated_at DESC);
CREATE INDEX ix_conversations_state_expires
    ON assistant.conversations (state, expires_at)
    WHERE expires_at IS NOT NULL;

CREATE TABLE assistant.conversation_messages (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES assistant.conversations(conversation_id) ON DELETE CASCADE,
    sequence_no BIGINT NOT NULL,
    author_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    redaction_state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_conversation_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT ck_conversation_message_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_conversation_message_author CHECK (author_type IN ('HUMAN','ASSISTANT','SYSTEM','TOOL')),
    CONSTRAINT ck_conversation_message_human_actor CHECK (author_type <> 'HUMAN' OR actor_id IS NOT NULL),
    CONSTRAINT ck_conversation_message_content_size CHECK (octet_length(content) BETWEEN 1 AND 32768),
    CONSTRAINT ck_conversation_message_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_conversation_message_classification CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
    CONSTRAINT ck_conversation_message_redaction CHECK (redaction_state IN ('NONE','REDACTED','QUARANTINED')),
    CONSTRAINT ck_conversation_message_revision CHECK (revision >= 0),
    CONSTRAINT ck_conversation_message_metadata_object CHECK (jsonb_typeof(metadata_json) = 'object'),
    CONSTRAINT ck_conversation_message_metadata_size CHECK (octet_length(metadata_json::text) <= 16384)
);

CREATE INDEX ix_conversation_messages_conversation_sequence
    ON assistant.conversation_messages (conversation_id, sequence_no);
