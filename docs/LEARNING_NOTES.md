# Pulse learning handbook

For each major feature, record: problem, step-by-step request/data flow, important classes/tables, design choice and alternatives, failure/concurrency behavior, tests, and interview explanation.

## Concepts already covered
### JWT
Authentication asks who is making the request; authorization asks what they may do. Bearer JWT is validated for signature/expiry; subject supplies identity. JWT alone does not enforce resource ownership.

### Password hashing
Do not store plaintext passwords. BCrypt is a password-hashing function designed to make guessing expensive; hashing is not reversible encryption.

### Idempotency
Clients retry requests when responses are lost. A scoped idempotency key identifies the same logical operation. Pulse's known design stores request hash and response, and atomically claims the key. Same key with different payload should conflict. Define retention/cleanup.

### Pagination
Pagination limits response size. Stable ordering needs a tie-breaker; known order is `created_at DESC, id DESC`. Offset pagination can become expensive at large offsets; consider keyset pagination only when requirements justify it.

### Indexes
An index is an auxiliary structure that can help locate/order rows. It costs storage and write work. Composite index order matters. Pulse's known service index starts with `user_id`, then `created_at DESC`, `id DESC`. Use `EXPLAIN (ANALYZE, BUFFERS)`; tiny tables may use sequential scans.

### Request IDs
`X-Request-ID` correlates a request and its log entries. Logs should include method/path/status/duration without secrets.

## Upcoming topics
Rate limiting algorithms; transactions/isolation/locking/deadlocks; Go goroutines/channels/worker pools/timeouts; SSRF/DNS rebinding; retries/backoff/jitter/circuit breakers/backpressure; Redis TTL/cache invalidation/distributed coordination; Kafka partitions/consumer groups/delivery semantics/idempotency/DLQ/outbox; API gateway/load balancing; metrics/logs/traces/Prometheus/Grafana/Kubernetes.

Do not memorize generated code line by line. Understand flows, invariants, failure modes, trade-offs and tests.
