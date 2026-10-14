CREATE TABLE assistant.buggy_task_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    task_id UUID NOT NULL,
    task_revision BIGINT NOT NULL CHECK (task_revision >= 1),
    previous_revision BIGINT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT NOT NULL CHECK (char_length(description) BETWEEN 1 AND 8192),
    status VARCHAR(32) NOT NULL CHECK (status IN ('OPEN','ACTIVE','WAITING_DEPENDENCY','WAITING_APPROVAL','PAUSED','COMPLETED','FAILED','CANCELLED')),
    attention VARCHAR(32) NOT NULL CHECK (attention IN ('INFO','NOTICE','ACTION_REQUIRED','URGENT','CRITICAL')),
    progress_percent INTEGER NOT NULL CHECK (progress_percent BETWEEN 0 AND 100),
    waiting_reason VARCHAR(1024) NULL,
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, task_id, task_revision),
    CONSTRAINT ck_buggy_task_chain CHECK (
        (task_revision = 1 AND previous_revision IS NULL)
        OR (task_revision > 1 AND previous_revision = task_revision - 1)
    ),
    CONSTRAINT ck_buggy_task_waiting CHECK (
        (status IN ('WAITING_DEPENDENCY','WAITING_APPROVAL') AND waiting_reason IS NOT NULL)
        OR (status NOT IN ('WAITING_DEPENDENCY','WAITING_APPROVAL') AND waiting_reason IS NULL)
    ),
    CONSTRAINT ck_buggy_task_completed_progress CHECK (status <> 'COMPLETED' OR progress_percent = 100),
    CONSTRAINT fk_buggy_task_previous
        FOREIGN KEY (assistant_id, task_id, previous_revision)
        REFERENCES assistant.buggy_task_revisions(assistant_id, task_id, task_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_task_head (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    task_id UUID NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, task_id),
    FOREIGN KEY (assistant_id, task_id, current_revision)
        REFERENCES assistant.buggy_task_revisions(assistant_id, task_id, task_revision) ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_task_relations (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    source_task_id UUID NOT NULL,
    target_task_id UUID NOT NULL,
    relation_type VARCHAR(32) NOT NULL CHECK (relation_type IN ('PARENT_CHILD','BLOCKED_BY','RELATED')),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, source_task_id, target_task_id, relation_type),
    CHECK (source_task_id <> target_task_id),
    FOREIGN KEY (assistant_id, source_task_id) REFERENCES assistant.buggy_task_head(assistant_id, task_id) ON DELETE RESTRICT,
    FOREIGN KEY (assistant_id, target_task_id) REFERENCES assistant.buggy_task_head(assistant_id, task_id) ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_activity (
    activity_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    activity_type VARCHAR(48) NOT NULL,
    attention VARCHAR(32) NOT NULL CHECK (attention IN ('INFO','NOTICE','ACTION_REQUIRED','URGENT','CRITICAL')),
    task_id UUID NULL,
    automation_id VARCHAR(128) NULL,
    summary VARCHAR(512) NOT NULL,
    details_encoded TEXT NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (task_id IS NOT NULL OR automation_id IS NOT NULL OR activity_type = 'OBSERVATION_RECEIVED')
);

CREATE INDEX idx_buggy_activity_recent ON assistant.buggy_activity(occurred_at DESC, activity_id DESC);
CREATE INDEX idx_buggy_activity_attention ON assistant.buggy_activity(attention, occurred_at DESC);

CREATE TABLE assistant.buggy_automation_revisions (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    automation_id VARCHAR(128) NOT NULL,
    automation_revision BIGINT NOT NULL CHECK (automation_revision >= 1),
    previous_revision BIGINT NULL,
    name VARCHAR(256) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('DRAFT','ENABLED','PAUSED','DISABLED')),
    trigger_type VARCHAR(16) NOT NULL CHECK (trigger_type IN ('SIGNAL','SCHEDULE')),
    signal_type VARCHAR(48) NULL,
    schedule_interval_seconds BIGINT NULL CHECK (schedule_interval_seconds IS NULL OR schedule_interval_seconds > 0),
    max_level VARCHAR(16) NOT NULL CHECK (max_level IN ('OBSERVE','ANALYZE','PREPARE','ACT','ESCALATE')),
    attention VARCHAR(32) NOT NULL CHECK (attention IN ('INFO','NOTICE','ACTION_REQUIRED','URGENT','CRITICAL')),
    action_tool_id VARCHAR(128) NOT NULL,
    capability_id VARCHAR(128) NOT NULL,
    risk_level VARCHAR(16) NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    max_attempts_per_target INTEGER NOT NULL CHECK (max_attempts_per_target BETWEEN 1 AND 3),
    cooldown_seconds BIGINT NOT NULL CHECK (cooldown_seconds > 0),
    window_seconds BIGINT NOT NULL CHECK (window_seconds > 0),
    max_actions_per_window INTEGER NOT NULL CHECK (max_actions_per_window BETWEEN 1 AND 100),
    same_target_suppression_seconds BIGINT NOT NULL CHECK (same_target_suppression_seconds > 0),
    failure_escalation_threshold INTEGER NOT NULL CHECK (failure_escalation_threshold BETWEEN 1 AND 3),
    activation_request_id UUID NULL,
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, automation_id, automation_revision),
    CONSTRAINT ck_buggy_automation_chain CHECK (
        (automation_revision = 1 AND previous_revision IS NULL)
        OR (automation_revision > 1 AND previous_revision = automation_revision - 1)
    ),
    CONSTRAINT ck_buggy_automation_trigger CHECK (
        (trigger_type = 'SIGNAL' AND signal_type IS NOT NULL AND schedule_interval_seconds IS NULL)
        OR (trigger_type = 'SCHEDULE' AND signal_type IS NULL AND schedule_interval_seconds IS NOT NULL)
    ),
    CONSTRAINT ck_buggy_automation_act_risk CHECK (max_level <> 'ACT' OR risk_level = 'LOW'),
    CONSTRAINT ck_buggy_automation_privileged_activation CHECK (state <> 'ENABLED' OR risk_level = 'LOW' OR activation_request_id IS NOT NULL),
    CONSTRAINT fk_buggy_automation_previous
        FOREIGN KEY (assistant_id, automation_id, previous_revision)
        REFERENCES assistant.buggy_automation_revisions(assistant_id, automation_id, automation_revision)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE assistant.buggy_automation_head (
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    automation_id VARCHAR(128) NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (assistant_id, automation_id),
    FOREIGN KEY (assistant_id, automation_id, current_revision)
        REFERENCES assistant.buggy_automation_revisions(assistant_id, automation_id, automation_revision) ON DELETE RESTRICT
);

CREATE TABLE assistant.buggy_automation_runs (
    run_id UUID PRIMARY KEY,
    assistant_id VARCHAR(16) NOT NULL REFERENCES assistant.buggy_identity_core(assistant_id) ON DELETE RESTRICT,
    automation_id VARCHAR(128) NOT NULL,
    automation_revision BIGINT NOT NULL,
    final_level VARCHAR(16) NOT NULL CHECK (final_level IN ('OBSERVE','ANALYZE','PREPARE','ACT','ESCALATE')),
    target_fingerprint VARCHAR(192) NOT NULL,
    successful BOOLEAN NOT NULL,
    safe_code VARCHAR(96) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CHECK (completed_at >= started_at),
    FOREIGN KEY (assistant_id, automation_id, automation_revision)
        REFERENCES assistant.buggy_automation_revisions(assistant_id, automation_id, automation_revision) ON DELETE RESTRICT
);

CREATE INDEX idx_buggy_automation_runs_latest
    ON assistant.buggy_automation_runs(automation_id, completed_at DESC);

CREATE OR REPLACE FUNCTION assistant.reject_buggy_b6_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Buggy B6 history is append-only';
END;
$$;

CREATE TRIGGER trg_buggy_task_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_task_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b6_history_mutation();

CREATE TRIGGER trg_buggy_task_relations_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_task_relations
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b6_history_mutation();

CREATE TRIGGER trg_buggy_activity_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_activity
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b6_history_mutation();

CREATE TRIGGER trg_buggy_automation_revisions_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_automation_revisions
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b6_history_mutation();

CREATE TRIGGER trg_buggy_automation_runs_immutable
BEFORE UPDATE OR DELETE ON assistant.buggy_automation_runs
FOR EACH ROW EXECUTE FUNCTION assistant.reject_buggy_b6_history_mutation();
