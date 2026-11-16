# Balance Control

Item balance and order control for public agencies — technical challenge (Full Stack, mid-level).

## Running

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

Put the legacy files in `data/`, either extracted or as the original zip (`massa-dados-candidato.zip`).
The `dataload` service loads them once (~3 minutes for 3.3M rows) and then exits. Later
`docker compose up` runs skip the load. `docker compose down -v` resets the database.
Without files, the app starts with an empty database.

| Service  | URL                                  |
|----------|--------------------------------------|
| Frontend | http://localhost:8081                |
| Backend  | http://localhost:8080/actuator/health |
| Postgres | localhost:5432 (`balance_control` / `balance_control`) |

Ports and credentials can be overridden by copying `.env.example` to `.env`.

## Development

```bash
cd backend && ./mvnw test          # 30 tests: rules, concurrency, data load (needs Docker: Testcontainers)
cd backend && ./mvnw spring-boot:run   # needs the db: docker compose up -d db
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to :8080
```

## Stack

Java 17, Spring Boot 2.7, Flyway, PostgreSQL 16, Vue 2.7 + Vuetify 2.7 (Vite), nginx.
Testing with JUnit 5 + Testcontainers against a real PostgreSQL.

## Decisions

Short ADRs in [`docs/adr`](docs/adr):

- [0001 — Stack and project structure](docs/adr/0001-stack-and-structure.md)
- [0002 — Legacy data load](docs/adr/0002-legacy-data-load.md)
- [0003 — Concurrency, idempotency and consistency](docs/adr/0003-concurrency-and-consistency.md)

## Screens

- **Orders:** list of the 1M orders (filters: agency, status, external id), detail, cancellation.
- **New order:** agency + items; the idempotency key is generated per form.
- **Items:** search, create, edit (concurrent edits show "your values vs current"),
  balance adjustments with history.
- **Data load:** what was loaded, the quarantine (every rejected row with file, line and reason)
  and the balance reconciliation.

## API

| Method | Path | Notes |
|---|---|---|
| GET | `/agencies?search=` | paginated |
| GET | `/items?search=&active=` | paginated |
| GET | `/items/{id}` | |
| POST | `/items` | 201; 409 `ITEM_CODE_ALREADY_EXISTS` |
| PUT | `/items/{id}` | description/unit/active + `version`; 409 `ITEM_VERSION_CONFLICT` with current state |
| POST | `/items/{id}/adjustments` | `{delta, reason}`; 422 if the balance would go negative |
| GET | `/items/{id}/adjustments` | adjustment history |
| GET | `/orders?agencyId=&status=&externalId=` | paginated, newest first |
| GET | `/orders/{id}` | with lines |
| POST | `/orders` | `{externalId, agencyCode, requester, origin, items[{itemCode, quantity}]}` — 201 new, **200 resend**, 422 `INSUFFICIENT_BALANCE` / `ITEM_NOT_FOUND` / `ITEM_INACTIVE` / `AGENCY_NOT_FOUND`, 409 `IDEMPOTENCY_KEY_REUSED` |
| POST | `/orders/{id}/cancel` | `{reason?}`; 409 `ORDER_ALREADY_CANCELLED` |
| GET | `/dataload/summary`, `/dataload/rejections`, `/dataload/divergences` | load report |

Errors always have the shape `{ "code": "...", "message": "...", "details": [...] }`.
Page size is capped at 100.

## Business rules and how they are enforced

| Rule | Guarantee (database) | Application | Test |
|---|---|---|---|
| R1 balance never negative | `CHECK (balance >= 0)` | conditional debits/adjustments; balance not writable through JPA | `databaseRejectsNegativeBalance...` |
| R2 order fulfilled whole or not at all | one transaction | any failed debit → exception → rollback; reports every shortage | `orderIsAllOrNothing` |
| R3 cancel once, balance back | `UPDATE ... WHERE status = 'CONFIRMED'` | 0 rows → 409; credits in the same transaction | `concurrentCancellationsReturnBalanceOnce` |
| R4 many concurrent orders | row lock of `UPDATE ... WHERE balance >= q` | lines locked in item id order (no deadlocks) | `concurrentOrdersNeverOversell`, `...OppositeOrderDoNotDeadlock` |
| R5 no silent lost update | `version` column | `@Version` on descriptive fields → 409 + current state; balance only by atomic deltas | `twoManagersSavingTheSameVersionAtOnceOnlyOneWins`, `editingDescriptionNeverOverwritesBalance...` |
| R6 same request never registered twice | `UNIQUE (external_id)` | insert order first; unique violation → return the existing order (200) | `concurrentResendsCreateExactlyOneOrder` |

Details and rejected alternatives: [ADR 0003](docs/adr/0003-concurrency-and-consistency.md).

**Messaging and cache are not used.** Every rule above is guaranteed by PostgreSQL in one
transaction. A queue would make order placement asynchronous: the client would no longer get
"fulfilled or not" in the response, and it would add infrastructure to operate without
removing any lock. A cache of balances would have to be invalidated on every debit, and a
stale balance is precisely the bug to avoid. The read paths are index-backed and fast
without it (see Performance).

## Data load

`COPY` to staging, then a SQL procedure validates, quarantines and reconciles, all in one
transaction. Summary of the results (details in [ADR 0002](docs/adr/0002-legacy-data-load.md)):

- 999,150 orders and 2,323,686 lines loaded; 9,209 rows quarantined with reasons.
- 2,000 resent orders (identical rows) are kept once (R6 applied to history).
- Orders with any invalid line (unknown item, invalid quantity) are rejected **whole** (R2).
- Units are normalized (` un` → `UN`).
- **Balances:** a naive recalculation shows 2,893 divergent items (328 negative), but only
  because it counts the duplicated lines of resent orders. After cleaning, the legacy current
  balance matches `initial − confirmed orders` for all 50,000 items.

## Assumptions

- The legacy current balance (`saldo_atual`) is the item balance. It agrees with the cleaned
  history; if a future file diverges, `saldo_atual` is kept and the divergence is reported.
- Legacy timestamps without a time zone are Brasília local time.
- An order with any invalid line is not loaded at all (it stays in quarantine for review).
- The portal sends an idempotency key too (one per order form), not only the integration.
- Same `externalId` with different content is a client error (409), not a new order.
- Inactive items can't be ordered, but orders that contain them can still be cancelled.
- No authentication: out of the challenge's scope.

## Performance (1M orders, local Docker)

| Query | Time |
|---|---|
| Orders, newest first (page 1 or page 5,000) | ~0.29 s (mostly the total `count(*)`) |
| Orders by agency / by status | 0.02 s / 0.09 s |
| Item search (50k) | 0.05 s |

The unfiltered order count is the slowest part. If it matters, the next step is an estimated
count or a "load more" (no total) list. It wasn't done, to keep normal pagination.

## AI usage

See [`docs/ai-usage.md`](docs/ai-usage.md).
