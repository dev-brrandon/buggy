# Database role model

Production uses three PostgreSQL roles:

- `legit_assistant_owner`: NOLOGIN object owner.
- `legit_assistant_migrator`: deployment-only login; may `SET ROLE legit_assistant_owner` for migrations.
- `legit_assistant_app`: runtime login; DML only, with append-oriented audit privileges.

Passwords/certificates are injected by the deployment secret store and never committed.
The runtime role must not own schemas/tables and must not receive `CREATE`, `ALTER`, `DROP`, role-management, superuser, replication, or bypass-RLS privileges.
