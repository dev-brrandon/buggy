CREATE TABLE assistant.buggy_memory_candidates (
    candidate_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    memory_type VARCHAR(32) NOT NULL CHECK (memory_type IN ('SESSION','EXPERIENCE','KNOWLEDGE','RELATIONSHIP','PRIMARY_OWNER')),
    subject_actor_id VARCHAR(128) NULL,
    memory_key VARCHAR(128) NOT NULL,
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 16384),
    provenance VARCHAR(24) NOT NULL CHECK (provenance IN ('EXPLICIT','OBSERVED','INFERRED','DERIVED','IMPORTED')),
    source_ref VARCHAR(1024) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    retention_class VARCHAR(24) NOT NULL CHECK (retention_class IN ('SESSION','SHORT_TERM','LONG_TERM','PERMANENT')),
    pinned BOOLEAN NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (memory_type NOT IN ('RELATIONSHIP','PRIMARY_OWNER') OR subject_actor_id IS NOT NULL),
    CHECK (memory_type <> 'SESSION' OR source_ref LIKE 'conversation:%'),
    CHECK (memory_type <> 'KNOWLEDGE' OR source_ref LIKE 'knowledge:%'),
    CHECK (memory_type <> 'PRIMARY_OWNER' OR retention_class <> 'SESSION'),
    CHECK (NOT pinned OR retention_class <> 'SESSION'),
    CHECK (memory_type <> 'PRIMARY_OWNER' OR retention_class <> 'SESSION')
);

CREATE TABLE assistant.buggy_memory_candidate_decisions (
    candidate_id UUID PRIMARY KEY REFERENCES assistant.buggy_memory_candidates(candidate_id) ON DELETE RESTRICT,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ACCEPTED','REJECTED')),
    memory_id UUID NULL,
    reason VARCHAR(1024) NOT NULL,
    decided_by VARCHAR(128) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CHECK ((decision = 'ACCEPTED' AND memory_id IS NOT NULL) OR (decision = 'REJECTED' AND memory_id IS NULL))
);

CREATE TABLE assistant.buggy_memory_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    memory_id UUID NOT NULL,
    memory_revision BIGINT NOT NULL CHECK (memory_revision >= 1),
    previous_revision BIGINT NULL,
    memory_type VARCHAR(32) NOT NULL CHECK (memory_type IN ('SESSION','EXPERIENCE','KNOWLEDGE','RELATIONSHIP','PRIMARY_OWNER')),
    subject_actor_id VARCHAR(128) NULL,
    memory_key VARCHAR(128) NOT NULL,
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 16384),
    provenance VARCHAR(24) NOT NULL CHECK (provenance IN ('EXPLICIT','OBSERVED','INFERRED','DERIVED','IMPORTED')),
    source_ref VARCHAR(1024) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    retention_class VARCHAR(24) NOT NULL CHECK (retention_class IN ('SESSION','SHORT_TERM','LONG_TERM','PERMANENT')),
    pinned BOOLEAN NOT NULL,
    memory_state VARCHAR(16) NOT NULL CHECK (memory_state IN ('ACTIVE','RETIRED')),
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, memory_id, memory_revision),
    CHECK ((memory_revision = 1 AND previous_revision IS NULL)
        OR (memory_revision > 1 AND previous_revision = memory_revision - 1)),
    CHECK (memory_type NOT IN ('RELATIONSHIP','PRIMARY_OWNER') OR subject_actor_id IS NOT NULL),
    CHECK (memory_type <> 'SESSION' OR source_ref LIKE 'conversation:%'),
    CHECK (memory_type <> 'KNOWLEDGE' OR source_ref LIKE 'knowledge:%'),
    CHECK (memory_type <> 'PRIMARY_OWNER' OR retention_class <> 'SESSION'),
    CHECK (NOT pinned OR retention_class <> 'SESSION'),
    CONSTRAINT fk_buggy_memory_previous
        FOREIGN KEY (assistant_id, memory_id, previous_revision)
        REFERENCES assistant.buggy_memory_revisions(assistant_id, memory_id, memory_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_memory_heads (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    memory_id UUID NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, memory_id),
    CHECK (updated_at >= created_at),
    FOREIGN KEY (assistant_id, memory_id, current_revision)
        REFERENCES assistant.buggy_memory_revisions(assistant_id, memory_id, memory_revision) ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_memory_heads_updated
    ON assistant.buggy_memory_heads(updated_at DESC, memory_id);

CREATE INDEX idx_buggy_memory_revisions_type_key
    ON assistant.buggy_memory_revisions(memory_type, memory_key, changed_at DESC);

CREATE INDEX idx_buggy_memory_candidates_created
    ON assistant.buggy_memory_candidates(created_at DESC, candidate_id);

CREATE TABLE assistant.buggy_reflections (
    reflection_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    summary VARCHAR(4096) NOT NULL,
    lesson VARCHAR(4096) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant.buggy_reflection_memory_refs (
    reflection_id UUID NOT NULL REFERENCES assistant.buggy_reflections(reflection_id) ON DELETE RESTRICT,
    assistant_id VARCHAR(16) NOT NULL,
    memory_ordinal INTEGER NOT NULL CHECK (memory_ordinal BETWEEN 0 AND 63),
    memory_id UUID NOT NULL,
    PRIMARY KEY (reflection_id, memory_ordinal),
    UNIQUE (reflection_id, memory_id),
    FOREIGN KEY (assistant_id, memory_id)
        REFERENCES assistant.buggy_memory_heads(assistant_id, memory_id) ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_learning_candidates (
    candidate_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    reflection_id UUID NOT NULL REFERENCES assistant.buggy_reflections(reflection_id) ON DELETE RESTRICT,
    rule_key VARCHAR(128) NOT NULL,
    proposal VARCHAR(4096) NOT NULL,
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_buggy_learning_candidates_created
    ON assistant.buggy_learning_candidates(created_at DESC, candidate_id);

CREATE TABLE assistant.buggy_self_capability_revisions (
    assistant_id VARCHAR(16) NOT NULL,
    capability_id VARCHAR(96) NOT NULL,
    capability_revision BIGINT NOT NULL CHECK (capability_revision >= 1),
    previous_revision BIGINT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('AVAILABLE','LIMITED','UNAVAILABLE')),
    confidence_bps INTEGER NOT NULL CHECK (confidence_bps BETWEEN 0 AND 10000),
    limitation VARCHAR(320) NULL,
    evidence_ref VARCHAR(512) NOT NULL,
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, capability_id, capability_revision),
    FOREIGN KEY (assistant_id, capability_id)
        REFERENCES assistant.buggy_native_self_capabilities(assistant_id, capability_id) ON DELETE RESTRICT,
    CHECK ((capability_revision = 1 AND previous_revision IS NULL)
        OR (capability_revision > 1 AND previous_revision = capability_revision - 1)),
    CHECK (status <> 'UNAVAILABLE' OR confidence_bps = 0),
    CONSTRAINT fk_buggy_self_capability_previous
        FOREIGN KEY (assistant_id, capability_id, previous_revision)
        REFERENCES assistant.buggy_self_capability_revisions(assistant_id, capability_id, capability_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_self_capability_heads (
    assistant_id VARCHAR(16) NOT NULL,
    capability_id VARCHAR(96) NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, capability_id),
    CHECK (updated_at >= created_at),
    FOREIGN KEY (assistant_id, capability_id, current_revision)
        REFERENCES assistant.buggy_self_capability_revisions(assistant_id, capability_id, capability_revision) ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_native_development_journal (
    entry_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    entry_type VARCHAR(40) NOT NULL CHECK (entry_type IN (
        'REFLECTION_RECORDED','LEARNING_CANDIDATE_CREATED','SELF_CAPABILITY_REVISED')),
    summary VARCHAR(2048) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant.buggy_native_development_journal_refs (
    entry_id UUID NOT NULL REFERENCES assistant.buggy_native_development_journal(entry_id) ON DELETE RESTRICT,
    ref_ordinal INTEGER NOT NULL CHECK (ref_ordinal BETWEEN 0 AND 63),
    source_ref VARCHAR(512) NOT NULL,
    PRIMARY KEY (entry_id, ref_ordinal),
    UNIQUE (entry_id, source_ref)
);

CREATE INDEX idx_buggy_native_development_journal_created
    ON assistant.buggy_native_development_journal(created_at DESC, entry_id);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_g15_append_only_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy G1.5 append-only history cannot be updated or deleted';
END;
$$;

CREATE OR REPLACE FUNCTION assistant.reject_buggy_g15_head_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy G1.5 current-head rows cannot be deleted';
END;
$$;

CREATE TRIGGER trg_buggy_memory_candidates_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_memory_candidates
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_memory_candidate_decisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_memory_candidate_decisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_memory_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_memory_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_memory_heads_no_delete
BEFORE DELETE ON assistant.buggy_memory_heads
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_head_delete();

CREATE TRIGGER trg_buggy_reflections_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_reflections
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_reflection_memory_refs_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_reflection_memory_refs
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_learning_candidates_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_learning_candidates
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_self_capability_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_self_capability_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_self_capability_heads_no_delete
BEFORE DELETE ON assistant.buggy_self_capability_heads
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_head_delete();

CREATE TRIGGER trg_buggy_native_development_journal_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_development_journal
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();

CREATE TRIGGER trg_buggy_native_development_journal_refs_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_native_development_journal_refs
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_g15_append_only_mutation();
