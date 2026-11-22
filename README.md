# Pulse

Pulse is a service-monitoring platform under active development. Its current deliverable is a Java API for account management and user-owned HTTP endpoint definitions. The API does not yet run scheduled endpoint checks or store monitoring results.

## Implemented features

- User registration and login, BCrypt password hashing, and stateless JWT authentication.
- Authenticated current-user endpoint and owner-scoped service definition operations.
- Create, list, retrieve, and delete monitored-service definitions. Listing supports pagination.
- Idempotent service creation using the `Idempotency-Key` request header.
- Request validation, API error handling, and request-ID logging.
- Flyway-managed PostgreSQL schema, Actuator health endpoints, and Springdoc OpenAPI documentation.
- Configurable Token Bucket rate limiting for login, registration, and authenticated API traffic.

The current “monitored service” operations store endpoint definitions; they do not perform HTTP checks or provide health history, uptime, or incident data.

## Technology stack

- Java 21 and Spring Boot 4.1.1
- Spring MVC, Spring Security, Spring Data JPA, and Hibernate
- PostgreSQL 18 for local development; Flyway for schema migrations
- JWT authentication with JJWT and BCrypt password hashing
- Springdoc OpenAPI / Swagger UI
- Maven Wrapper for building and testing
- Docker Compose for the local PostgreSQL service

## Architecture

```text
Client
  │ HTTPS/JSON (HTTP on local development)
  ▼
Spring Boot API ───── PostgreSQL
  ├─ authentication and user APIs
  ├─ user-owned service definition APIs
  └─ in-memory rate-limit store
```

The API obtains a user UUID from the authenticated JWT principal and uses it for user-scoped service access. PostgreSQL is the persistent source of truth. Flyway applies schema migrations at application startup. The rate-limit store is process-local and is not shared through PostgreSQL.

## Local development

### Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- PowerShell on Windows (commands below use PowerShell)

### Start PostgreSQL

From the repository root, start the PostgreSQL 18 service defined in `infrastructure/docker/docker-compose.yml`:

```powershell
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml ps
```

Local Compose defaults are database `pulse`, user `pulse`, password `pulse`, and host port `5432`. These credentials are for local development only; do not reuse them in production. The named volume keeps database data across container restarts. To stop the service without deleting its data:

```powershell
docker compose -f infrastructure/docker/docker-compose.yml stop
```

### Run the API

From the repository root:

```powershell
Set-Location api
$env:JWT_SECRET = 'local-development-secret-change-this-to-a-random-value-32-bytes-minimum'
.\mvnw.cmd spring-boot:run
```

The JWT secret must be at least 32 bytes for the configured HMAC signing algorithm. The value above is only an example for local development; use a securely generated secret from a secret manager or environment configuration outside development. The datasource settings are in `api/src/main/resources/application.properties`. Flyway manages schema changes; Hibernate validates the resulting schema.

### Run tests

Start the local PostgreSQL service first. The test profile points to PostgreSQL on `127.0.0.1:5432` and uses a test-only JWT secret from `api/src/test/resources/application-test.properties`.

```powershell
Set-Location api
.\mvnw.cmd test
```

The suite includes unit, MVC/security integration, and PostgreSQL-backed tests. The database-backed tests require the local database to be available.

## API documentation and health

With the API running locally:

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI document: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Principal API routes:

| Method | Path | Purpose | Access |
| --- | --- | --- | --- |
| `POST` | `/api/v1/users/register` | Create an account | Public |
| `POST` | `/api/v1/auth/login` | Authenticate and receive a JWT | Public |
| `GET` | `/api/v1/users/me` | Return the authenticated user identity | JWT |
| `POST` | `/api/v1/services` | Create a service definition; requires `Idempotency-Key` | JWT |
| `GET` | `/api/v1/services` | List the current user’s services (`page`, `size`) | JWT |
| `GET` | `/api/v1/services/{serviceId}` | Retrieve an owned service definition | JWT |
| `DELETE` | `/api/v1/services/{serviceId}` | Delete an owned service definition | JWT |

## Rate limiter

The current Token Bucket policies are:

| Request class | Key | Capacity and refill |
| --- | --- | --- |
| Login | Remote IP | 5 tokens; 1 token every 12 seconds |
| Registration | Remote IP | 3 tokens; 1 token every 20 minutes |
| Authenticated API | Authenticated user UUID | 60 tokens; 1 token per second |

Login and registration use separate buckets. Swagger/OpenAPI, health, and CORS `OPTIONS` requests are excluded. A rejected request receives HTTP 429, an `ErrorResponse` JSON body, and a `Retry-After` header. The `TokenBucketStore` interface separates storage from the filter; the current implementation is bounded, in-memory, and single-instance. Limits reset on restart and are not shared between API instances. Redis-backed storage is not implemented.

## Implementation status

### Implemented

The API supports account authentication, ownership-scoped service definition CRUD, pagination, idempotent creation, database migrations, health and OpenAPI endpoints, request-ID logging, and in-memory rate limiting.

### Planned

Scheduled HTTP checks, worker scheduling and execution, monitoring result history, uptime/latency views, incident detection, event streaming, and a dashboard are not implemented. Go workers, Redis, Kafka, a React dashboard, Kubernetes, and cloud deployment remain future options or roadmap items; they are not part of the current application.

For project notes, decisions, setup details, security guidance, and roadmap, see the [documentation index](docs/README.md).
