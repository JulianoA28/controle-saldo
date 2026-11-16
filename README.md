# Balance Control

Item balance and order control for public agencies — technical challenge (Full Stack, mid-level).

## Running

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

| Service  | URL                                  |
|----------|--------------------------------------|
| Frontend | http://localhost:8081                |
| Backend  | http://localhost:8080/actuator/health |
| Postgres | localhost:5432 (`balance_control` / `balance_control`) |

Ports and credentials can be overridden by copying `.env.example` to `.env`.

## Development

```bash
cd backend && ./mvnw test          # unit + integration tests (needs Docker: Testcontainers)
cd backend && ./mvnw spring-boot:run   # needs the db: docker compose up -d db
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to :8080
```

## Stack

Java 17, Spring Boot 2.7, Flyway, PostgreSQL 16, Vue 2.7 + Vuetify 2.7 (Vite), nginx.
Testing with JUnit 5 + Testcontainers against a real PostgreSQL.

## Decisions

Short ADRs in [`docs/adr`](docs/adr):

- [0001 — Stack and project structure](docs/adr/0001-stack-and-structure.md)

## Business rules and how they are enforced

_To be filled in as each rule is implemented._

## Data load

_To be filled in: legacy CSV findings, cleaning rules and quarantine._

## Assumptions

_To be filled in: e.g. which balance is the source of truth for legacy items._

## AI usage

See [`docs/ai-usage.md`](docs/ai-usage.md).
