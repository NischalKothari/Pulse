# Pulse security requirements

## API security reported implemented (verify)
- Stateless Spring Security and JWT Bearer authentication.
- JWT subject contains user UUID.
- Public registration/login; protected user/service endpoints.
- BCrypt password hashing.
- Safe standardized JSON errors.
- Owner-scoped service retrieval/deletion.

## Authorization
Derive identity from authenticated principal. Never trust client-supplied owner ID. Enforce ownership in service/data access. Test cross-user reads/updates/deletes against a real database.

## Secrets
Never commit JWT signing keys, production passwords, tokens or API keys. Current `pulse`/`pulse` DB credentials are local-only. Use environment variables or a secret manager outside local development. Never log credentials or Authorization headers.

## SSRF — mandatory before monitoring user URLs
Treat URLs as untrusted. Before outbound requests:
- Restrict schemes (normally HTTP/HTTPS).
- Block loopback, private, link-local, multicast, reserved and metadata-service destinations according to policy.
- Resolve and validate DNS/IP destinations; account for DNS rebinding.
- Revalidate redirects or disable them.
- Restrict ports where possible.
- Apply connect/read/overall timeouts and response-size limits.
- Avoid leaking sensitive URL query parameters or response bodies in logs.
Review and test the design before enabling monitoring.

## Abuse and error handling
The API applies configurable token-bucket rate limits. Login uses an IP bucket (capacity 5, one token per 12 seconds); registration uses a separate IP bucket (capacity 3, one token per 20 minutes); authenticated API requests use a user UUID bucket (capacity 60, one token per second). CORS OPTIONS, health, and Swagger/OpenAPI requests are excluded.

The current store is bounded and in-memory, so limits are per application process and reset on restart. Multiple instances do not share counts; Redis is a future `TokenBucketStore` implementation, not part of the current deployment. Anonymous identity uses the servlet remote address. Do not trust spoofable forwarded-IP headers unless the deployment configures trusted proxies.

Rate-limited requests receive HTTP 429 with the standard `ErrorResponse` body and `Retry-After`. If the bounded in-memory store has no room for a new key, new requests are also rejected with a retry delay until an idle entry can expire. Return safe generic 5xx errors; never expose stack traces or secrets.

## Review checklist
Authentication? Authorization/ownership? Input validation? Secrets? Abuse controls? Concurrency/replay? Safe failures? Tests for unauthorized and cross-user behavior?
