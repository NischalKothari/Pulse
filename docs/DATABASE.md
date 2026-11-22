# Pulse database reference

PostgreSQL is the durable source of truth. Local dev moved from PostgreSQL 16 to PostgreSQL 18. A fresh PostgreSQL 18.7 container was started and queried; old data was intentionally not restored. A separate copy of the previous Docker VHDX was retained, but it is not an active database backup unless restored and validated.

## Known migration history (from prior project inspection; re-verify after fresh DB migration)

### V1 users
`id UUID PK`, `email VARCHAR(255) NOT NULL UNIQUE`, `password_hash VARCHAR(255) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`.

### V2 services
`id UUID PK`, `user_id UUID NOT NULL` FK users, `name VARCHAR(100) NOT NULL`, `url TEXT NOT NULL`, `check_interval_seconds INTEGER NOT NULL CHECK (>0)`, `timeout_ms INTEGER NOT NULL CHECK (>0)`, `status VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`.

### V3
Sets service `status` default to `UNKNOWN`.

### V4 idempotency_records
`user_id UUID`, `operation VARCHAR(80)`, `idempotency_key VARCHAR(255)`, `request_hash CHAR(64)` initially, nullable `response_status INTEGER`, nullable `response_body TEXT`, `created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP`; FK to users; unique `(user_id, operation, idempotency_key)`; check requires response status/body both null or both non-null.

### V5
Changes `request_hash` to `VARCHAR(64)`.

### V6
Adds `version BIGINT NOT NULL DEFAULT 0` to services.

### V7
```sql
CREATE INDEX idx_services_user_created_id
ON services (user_id, created_at DESC, id DESC);
```

## Known list query
```sql
WHERE user_id = ?
ORDER BY created_at DESC, id DESC
LIMIT ? OFFSET ?
```
Index follows this pattern. Planner may correctly choose sequential scan for tiny tables.

## Next database work
Verify fresh PostgreSQL 18 Flyway migration; inspect `flyway_schema_history`; later design check results, aggregates, incidents, outbox, retention/partitioning. Add indexes based on actual query patterns and plans.
