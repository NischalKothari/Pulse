# Pulse roadmap

Status labels: **reported implemented** means prior project history says it exists; verify current code. **Planned** means not yet implemented in that history. Do not claim completion without evidence.

## Phase 1 — Core backend foundation
Reported implemented; re-verify after fresh PostgreSQL 18 setup:
- Registration/login, BCrypt, stateless JWT/Spring Security.
- User identity from authenticated principal.
- Service CRUD and owner-scoped retrieval/deletion.
- Pagination with stable ordering.
- Idempotent service creation.
- Configurable token-bucket limits for login, registration and authenticated API traffic (bounded in-memory, single-instance implementation).
- Validation and standardized errors.
- Flyway migrations.
- Actuator health and Springdoc OpenAPI.
- Request ID response header and structured completion logs.
- Database integration tests for pagination and cross-user ownership.

## Phase 2 — Production API
- Shared rate-limit storage (such as Redis) and defined outage behavior before horizontal API scaling.
- API versioning and compatibility.
- OpenAPI completeness.
- Error/validation contract review.
- Real DB/security integration tests.
- Idempotency retention/cleanup policy.
- Health/readiness behavior.

## Phase 3 — Database correctness
- Transactions and rollback.
- Isolation levels, lost updates, write skew.
- Optimistic/pessimistic locking; assess existing `version` column.
- Deadlocks and prevention.
- Query plans/indexes based on real workload.
- High-volume result retention/partitioning.

## Phase 4 — Go monitoring engine
- HTTP checks, timeouts, bounded concurrency and worker pools.
- Scheduling and due-work claiming.
- Retry/backoff with jitter, circuit breakers, backpressure.
- SSRF protection before outbound requests.
- Result persistence/integration and load/failure tests.

## Phase 5 — Redis
Caching, TTL/invalidation, shared rate-limit state if required, distributed coordination only when justified, outage/fallback behavior.

## Phase 6 — Kafka
Event schemas/versioning, producers/consumers, consumer groups/partitioning, at-least-once delivery, idempotent consumers, retry topics/DLQ, outbox if needed, incident/notification consumers.

## Phase 7 — Distributed scaling
API gateway evaluation, load balancing, horizontal scaling, worker partitioning, backpressure, graceful shutdown and capacity/load testing.

## Phase 8 — Dashboard and delivery
React dashboard, Docker images/Compose, CI/CD, logs, Prometheus/Grafana, distributed tracing, Kubernetes when justified, cloud deployment, secrets, backups and restore drills.

## Completion gate
Acceptance criteria met; relevant tests actually pass; security/failure modes reviewed; docs updated; owner understands decisions; no invented metrics.
