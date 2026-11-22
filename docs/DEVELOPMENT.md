# Pulse local development

## Known environment
Windows, IntelliJ IDEA Community Edition, Java 21, Docker Desktop with WSL2, DataGrip optional. Confirm Maven wrapper and actual project profiles in repository.

## PostgreSQL 18 local Compose
Known configuration:
- image `postgres:18`
- container `pulse-postgres`
- DB/user/password: `pulse` / `pulse` / `pulse` (local development only)
- host port `5432`
- named volume `postgres_data` (actual Compose-generated name may be prefixed)
- PostgreSQL 18 mount target `/var/lib/postgresql`

Start from directory containing Compose file:
```powershell
docker compose up -d
docker ps
docker exec pulse-postgres pg_isready -U pulse -d pulse
docker exec pulse-postgres psql -U pulse -d pulse -c "SELECT version();"
```

Stop without deleting data:
```powershell
docker compose stop
```
Do not use `docker compose down -v` except for an explicitly approved data reset after backup.

## DataGrip
Host `localhost`, port `5432`, database `pulse`, user `pulse`, password `pulse`.

## Spring Boot datasource shape
Inspect actual active profile/config first:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pulse
spring.datasource.username=pulse
spring.datasource.password=pulse
```

## API rate limits
The default development token-bucket policies are configured with `pulse.rate-limit.*` properties in `application.properties`:
- Login: 5 tokens, refilling at 1 token per 12 seconds, per remote IP.
- Registration: 3 tokens, refilling at 1 token per 20 minutes, per remote IP; separate from login.
- Authenticated API: 60 tokens, refilling at 1 token per second, per authenticated user UUID.

OPTIONS, `/actuator/health`, Swagger UI, and OpenAPI endpoints are excluded. The bounded in-memory implementation is per process and resets when the API restarts; it does not coordinate limits across API instances.

## Flyway and tests
Start app or use configured Flyway task, inspect logs and `flyway_schema_history`. Use repository wrapper if present:
```powershell
.\mvnw.cmd test
```
Otherwise:
```powershell
mvn test
```

## Useful commands
```powershell
docker ps
docker logs pulse-postgres
docker exec -it pulse-postgres psql -U pulse -d pulse
docker compose config
```

## Troubleshooting
If Docker pipe is missing, start Docker Desktop and wait for engine; `docker version` should show Client and Server. If port 5432 is occupied, identify process first. If DB connection fails, check container, port, credentials and active Spring profile. For Flyway failure, inspect first error; don't rewrite applied migrations. Preserve VHDX backups and volumes; don't use prune/reset as generic troubleshooting.
