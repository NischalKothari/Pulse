# Pulse technical decisions

## Current direction (verify against repository)
- Java 21 + Spring Boot for API.
- PostgreSQL as durable source of truth; local dev upgraded from PostgreSQL 16 to 18.
- Flyway for schema migrations; never edit applied migrations.
- Spring Security + stateless JWT; JWT subject carries user UUID.
- BCrypt for password hashing.
- Springdoc OpenAPI for API docs.
- Go for monitoring workers (planned).
- Kafka for health-event streaming (planned).
- Redis for cache/shared coordination where justified (planned).
- React dashboard (planned).
- Actuator and request-ID structured logging reported implemented; Prometheus/Grafana/tracing planned.

## Idempotent service creation
Known design: unique `(user_id, operation, idempotency_key)` scope, request hash, stored response, atomic claim using PostgreSQL `ON CONFLICT DO NOTHING`. Reusing a key with a different payload should conflict. Retention policy remains undecided.

## Request IDs
Known response header: `X-Request-ID`; request completion log includes method, path, status and duration. Verify current implementation.

## Service-list index
Reported V7 index:
`(user_id, created_at DESC, id DESC)`.
It matches user filter plus stable descending order. Small-table EXPLAIN still used sequential scan/sort; this was expected. No performance improvement was proven by that experiment.

## Scale/retention targets
10,000 services, one check/minute (~167 checks/sec average, 14.4M/day); raw checks 7 days; aggregates 1 year; optional S3 archive. Targets only, not measured capacity.

## Open decisions
Rate-limit algorithm/policies; worker scheduling/lease design; monitoring result schema; Kafka event schema/partition key/outbox; Redis outage behavior; API gateway need; deployment target; secrets management; backup/restore objectives.
