# Pulse — Agent Instructions

## Mission
Pulse is a service-monitoring platform: users register HTTP endpoints; monitoring workers check them, record health/latency, detect incidents, and publish events for downstream processing.

## First action
On first entry, perform a READ-ONLY repository audit. Inspect source, tests, build files, migrations, configuration and Compose files. Compare actual code with these docs. Report discrepancies and wait for approval before editing application code.

## Working rules
- Inspect relevant code and tests before proposing changes.
- Give a concise plan and acceptance criteria before substantial work.
- Keep changes scoped; avoid unrelated refactors.
- Preserve existing API contracts unless approved.
- Add meaningful tests and report only tests actually run.
- Explain important code paths, trade-offs, failure modes and interview-relevant concepts in plain language.
- Update docs when architecture, APIs, schema or operations change.
- Never invent features, test results, benchmarks or performance claims.

## Ask before
Architecture changes; dependency/version changes; API contract changes; schema changes; authentication/security changes; destructive commands; deleting data, volumes, migrations or tests; deployments; commits or pushes.

## Database rules
- PostgreSQL is the source of truth; Flyway manages schema.
- Never edit an already-applied migration. Add a new version.
- Preserve data. Never run `docker compose down -v`, Docker prune, WSL unregister/reset, or database reset without explicit approval.
- Local development target: PostgreSQL 18, DB `pulse`, user `pulse`, host port `5432`. Password `pulse` is local-only and must never be reused in production.
- Confirm actual Compose project and volume names; Compose may prefix volume names.

## Security rules
- Derive user identity from the authenticated principal; enforce ownership in service/data access.
- Hash passwords; never log passwords, tokens or Authorization headers.
- Never commit secrets.
- SSRF protection is mandatory before any user-controlled URL is fetched. Validate schemes, DNS/IP destinations, redirects, ports, timeouts and response sizes.
- Return safe errors; do not leak stack traces or internal details.

## Quality
- Prefer clear code and existing conventions.
- Consider transactions, retries, idempotency, timeouts, concurrency and partial failures.
- Do not target arbitrary coverage percentages; test meaningful behavior and failure paths.
- Java: relevant Maven tests. Go: `go test ./...` and race detector where useful.
- Verify migrations against PostgreSQL when possible.

## Learning
For each major feature explain: problem, request/data flow, important classes, design alternatives, failure/concurrency behavior, tests, and interview explanation. Codex can implement boilerplate and scoped features, but must not replace the owner's understanding.

## Accuracy labels
Distinguish implemented-and-verified, reported-implemented, planned, proposed, and unknown. These docs are a starting baseline from project history; verify the repository.
