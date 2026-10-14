#!/bin/sh
set -eu

: "${POSTGRES_DB:?POSTGRES_DB required}"
: "${POSTGRES_USER:?POSTGRES_USER required}"
: "${LEGIT_ASSISTANT_POSTGRES_PASSWORD:?LEGIT_ASSISTANT_POSTGRES_PASSWORD required}"
: "${LEGIT_ASSISTANT_MIGRATOR_PASSWORD:?LEGIT_ASSISTANT_MIGRATOR_PASSWORD required}"

DB_ROOT=/opt/legit/assistant-db
MIGRATIONS="$DB_ROOT/migration"

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=app_password="$LEGIT_ASSISTANT_POSTGRES_PASSWORD" \
  --set=migrator_password="$LEGIT_ASSISTANT_MIGRATOR_PASSWORD" \
  --set=db_name="$POSTGRES_DB" <<'SQL'
CREATE ROLE legit_assistant_owner NOLOGIN;
CREATE ROLE legit_assistant_migrator LOGIN NOINHERIT PASSWORD :'migrator_password';
CREATE ROLE legit_assistant_app LOGIN NOINHERIT PASSWORD :'app_password';
GRANT legit_assistant_owner TO legit_assistant_migrator;
GRANT CONNECT ON DATABASE :"db_name" TO legit_assistant_migrator, legit_assistant_app;
GRANT CREATE ON DATABASE :"db_name" TO legit_assistant_owner;
SQL

for migration in \
  V4_001__create_domain_schemas.sql \
  V4_002__create_platform_metadata.sql \
  V4_003__seed_foundation_metadata.sql \
  V5_001__create_conversation_tables.sql \
  V5_002__seed_conversation_foundation_metadata.sql \
  V7_001__create_knowledge_tables.sql \
  V7_002__seed_knowledge_foundation_metadata.sql \
  V8_001__create_bug_tables.sql \
  V8_002__seed_bug_foundation_metadata.sql \
  V9_001__create_request_approval_tables.sql \
  V9_002__seed_request_approval_foundation_metadata.sql \
  V100_001__create_buggy_identity_runtime.sql \
  V100_002__create_buggy_personality_evolution.sql \
  V100_003__create_buggy_knowledge_annotations.sql \
  V100_004__create_buggy_tasks_activity_automation.sql \
  V100_005__create_buggy_self_development.sql
do
  test -f "$MIGRATIONS/$migration" || { echo "Missing migration: $migration" >&2; exit 1; }
  printf 'SET ROLE legit_assistant_owner;\n\\i %s\nRESET ROLE;\n' "$MIGRATIONS/$migration" \
    | psql -v ON_ERROR_STOP=1 -1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -f "$DB_ROOT/admin/002_schema_grants.sql.example"

printf 'Assistant database initialization complete.\n'
