# Pulse architecture

## Purpose
The current API lets users register and manage HTTP endpoint definitions. Periodic checks, health history, incident detection, and recent-event APIs remain target capabilities; no monitoring worker or results model is present yet.

## Target architecture
```text
React dashboard (planned)
        | HTTPS/JSON
Spring Boot API (Java 21)
   |             |
PostgreSQL      Redis (planned)
(source of truth; cache/coordination)
   ^
Go monitoring workers (planned)
   |
Kafka events (planned)
   |
Incident / notification consumers (planned)
```

This is the intended architecture, not a claim that all components exist.

## Technology direction
- API: Java 21, Spring Boot.
- Database: PostgreSQL; local setup upgraded from 16 to 18.
- Schema: Flyway.
- Security: Spring Security, JWT, BCrypt.
- API docs: Springdoc OpenAPI.
- Monitoring engine: Go (planned).
- Event streaming: Kafka (planned).
- Cache/coordination: Redis (planned).
- Dashboard: React (planned).
- Observability: Actuator health and request-ID completion logs in formatted text are implemented; Prometheus, Grafana and tracing are planned.
- Rate limiting: configurable token buckets for login, registration, and authenticated API traffic; the initial store is bounded and process-local. Redis remains planned.
- Delivery: Docker Compose local; CI/CD and Kubernetes planned.

## API capabilities reported in prior project work
Registration/login, JWT-protected `/api/v1/users/me`, service create/list/get/delete, owner-scoped access, pagination, idempotent create using `Idempotency-Key`, validation, standardized errors, Actuator health, OpenAPI and request-ID logging. Verify each in the current repository.

## Planned monitoring flow
1. User registers URL and check settings.
2. Go worker obtains due work.
3. Validate destination under SSRF policy before outbound access.
4. Perform bounded HTTP check with timeout/concurrency limits.
5. Persist result and/or publish event according to approved design.
6. Detect incident state transitions and notify.
7. Dashboard reads health, latency, uptime, incidents and events.

Exact scheduling, persistence and event-ordering designs remain to be decided.

## API rate limiting
The Spring Security chain runs the rate-limit filter after authorization so unauthorized protected requests retain the existing 401 response. Separate IP buckets protect login and registration; authenticated API traffic is keyed by the UUID principal. OPTIONS, health, and Swagger/OpenAPI requests bypass the limiter. The in-memory store is atomic and bounded, but is single-instance and resets on restart; a shared Redis adapter can later implement the `TokenBucketStore` abstraction.

## Scale and retention goals
Design targets: 10,000 services checked once/minute (~167 checks/sec average; 14.4M checks/day); raw checks retained 7 days; aggregates 1 year; optional S3 archive. These are targets, not measured achievements.

## Verify against repository
Confirm actual modules/packages, routes, DTOs, monitoring result schema, Go/Kafka/Redis/React status, transaction boundaries and event publication design.
