# Balance Control

Item balance and order control for public agencies — technical challenge (Full Stack, mid-level).

## Quick start

```bash
git clone https://github.com/JulianoA28/controle-saldo.git
cd controle-saldo
mkdir -p data && cp /path/to/massa-dados-candidato.zip data/   # or the 4 extracted CSVs
docker compose up --build
```

Open http://localhost:8081 once the `dataload` service prints `done` (about 3 minutes).

## Running

### Requirements

| To... | You need |
|---|---|
| Run everything | Docker with Compose v2 (`docker compose`, not `docker-compose`); about 1 GB of RAM and 3 GB of disk (images + 600 MB database) |
| Run the backend tests | Docker running + JDK 17 (Maven comes with `./mvnw`) |
| Run the frontend in dev mode | Node.js 18+ (tested with 22) |

Free ports **8080**, **8081** and **5432** (or change them, see [Configuration](#configuration)).

### 1. Put the legacy data in `data/`

The files are **not** in the repository (too large; `data/` is in `.gitignore`). Either form works:

```
data/massa-dados-candidato.zip          ← the original zip, as received
```
or
```
data/orgaos.csv
data/itens.csv
data/pedidos.csv
data/pedido_itens.csv
```

Without files the app still starts, with an empty database (items can be created on the
Items screen; agencies only come from the load).

### 2. Start

```bash
docker compose up --build
```

This starts four services:

| Service | What it does | Lifetime |
|---|---|---|
| `db` | PostgreSQL 16 (data in the `db-data` volume) | keeps running |
| `backend` | Spring Boot API; Flyway creates the schema on startup | keeps running |
| `frontend` | nginx serving the Vue app and proxying `/api` to the backend | keeps running |
| `dataload` | waits for the schema, `COPY`s the CSVs into staging tables and calls `dataload_process()` | **exits** when done |

The first build downloads Maven and npm dependencies (a few minutes). The load takes
**about 3 minutes** for the 3.3M rows. To follow it from another terminal:

```bash
docker compose logs -f dataload
```

It ends with a summary like this (these are the real numbers for the provided files):

```
[dataload] done in 177s:
{"items": 50000, "orders": 999150, "agencies": 250, "orderLines": 2323686, "rejections": 9209, "divergentItems": 0, ...}
```

`dataload exited with code 0` is expected: it is a one-shot job. The load is a single
transaction, so until it commits the screens show an empty database. Avoid creating data
during those minutes.

### 3. Use it

| Service  | URL |
|----------|-----|
| Frontend | http://localhost:8081 |
| Backend health | http://localhost:8080/actuator/health |
| API (direct) | http://localhost:8080 (e.g. `/orders`, `/items`) |
| API (through nginx, as the browser uses it) | http://localhost:8081/api |
| Postgres | `localhost:5432`, database/user/password `balance_control` |

A quick check that everything is up and loaded:

```bash
curl -s localhost:8080/actuator/health          # {"status":"UP"}
curl -s localhost:8080/dataload/summary         # "loaded":true, stats as above
```

### Stopping, restarting, resetting

```bash
docker compose down        # stop; data is kept in the db-data volume
docker compose up          # start again; the load is skipped ("already loaded; nothing to do")
docker compose down -v     # stop AND delete the database; the next "up" loads the data again
```

The load runs only on an empty database. If you start without files, create data in the
app and add the files later, run `docker compose down -v` first.

### Configuration

Copy `.env.example` to `.env` to change ports or credentials:

```
DB_PORT=5432
BACKEND_PORT=8080
FRONTEND_PORT=8081
DB_NAME=balance_control
DB_USER=balance_control
DB_PASSWORD=balance_control
```

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `port is already allocated` | Another process uses 8080/8081/5432: set other ports in `.env`. |
| `dataload` prints `no CSVs or zip found` | `data/` is empty or the files are in a subfolder: put them directly in `data/`. |
| `dataload` prints `missing /data/....csv` | The zip or folder doesn't have all 4 files. |
| `Data load needs empty domain tables` | The database already has data from another source: `docker compose down -v`. |
| The Data load screen says "No load has run yet" | The load is still running (see `docker compose logs -f dataload`) or no files were found. |
| Tests fail with `client version 1.32 is too old` | Already handled by `backend/src/test/resources/docker-java.properties` (Docker Engine 29+). Check that Docker is running. |
| On Windows, `load.sh: set: illegal option -` | The script was checked out with CRLF line endings. `.gitattributes` now forces LF for `*.sh`: clone the repository again. |

## Development

```bash
cd backend && ./mvnw test                  # 30 tests (rules, concurrency, data load); needs Docker
docker compose up -d db                    # database only, for local development
cd backend && ./mvnw spring-boot:run       # API on :8080, against the db above
cd frontend && npm install && npm run dev  # http://localhost:5173, proxies /api to :8080
```

The tests use Testcontainers: each run starts its own PostgreSQL 16 container, independent
of the one from `docker compose`. Concurrency tests release up to 100 threads at once
against it (H2 would not reproduce PostgreSQL locking).

## Try the API

```bash
# Place an order (201). Use a real agency code and item code from the loaded data.
ID=$(cat /proc/sys/kernel/random/uuid)     # or: uuidgen
BODY='{"externalId":"'$ID'","agencyCode":"ORG-0001","requester":"me","origin":"INTEGRATION",
       "items":[{"itemCode":"ITM-000001","quantity":2}]}'
curl -i -H 'Content-Type: application/json' -d "$BODY" localhost:8080/orders

# Resend the same request (R6): 200 with the same order, balance debited only once.
curl -i -H 'Content-Type: application/json' -d "$BODY" localhost:8080/orders

# Cancel it (R3): the balance comes back. A second cancel returns 409 ORDER_ALREADY_CANCELLED.
curl -i -X POST localhost:8080/orders/<id>/cancel
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
| POST | `/orders` | `{externalId, agencyCode, requester, origin, items[{itemCode, quantity}]}` — 201 new, **200 resend**, 422 `INSUFFICIENT_BALANCE` / `ITEM_NOT_FOUND` / `ITEM_INACTIVE` / `AGENCY_NOT_FOUND`, 409 `IDEMPOTENCY_KEY_REUSED`, 400 `DUPLICATE_ITEM` |
| POST | `/orders/{id}/cancel` | `{reason?}`; 409 `ORDER_ALREADY_CANCELLED`, 404 `ORDER_NOT_FOUND` |
| GET | `/dataload/summary`, `/dataload/rejections`, `/dataload/divergences` | load report |

Errors always have the shape `{ "code": "...", "message": "...", "details": [...] }`.
Paginated endpoints take `page` (zero-based) and `size` (default 20, capped at 100).

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
  Every source row is accounted for: loaded + quarantined = rows in the file.
- 2,000 resent orders (identical rows) are kept once (R6 applied to history).
- Orders with any invalid line (unknown item, invalid quantity) are rejected **whole** (R2).
- Units are normalized (` un` → `UN`).
- **Balances:** a naive recalculation shows 2,893 divergent items (328 negative). It counts
  the duplicated lines of resent orders, orders from unknown agencies and orders with invalid
  lines, none of which the legacy system counted. After the same cleaning the load applies,
  the legacy current balance matches `initial − confirmed orders` for all 50,000 items.

## Assumptions

- The legacy current balance (`saldo_atual`) is the item balance. It agrees with the cleaned
  history; if a future file diverges, `saldo_atual` is kept and the divergence is reported.
- Legacy timestamps without a time zone are Brasília local time.
- An order with any invalid line is not loaded at all (it stays in quarantine for review).
- The portal sends an idempotency key too (one per order form), not only the integration.
- Same `externalId` with different content (agency or items) is a client error (409), not a
  new order. Requester and origin are not compared.
- Inactive items can't be ordered, but orders that contain them can still be cancelled.
- No authentication: out of the challenge's scope.

## Performance (1M orders, local Docker)

Measured through the API with the full load (2026-11-19, WSL2 laptop):

| Query | Time |
|---|---|
| Orders, newest first (page 1 / page 5,000) | 0.03 s / 0.06 s |
| Orders by agency / by status | 0.01 s / 0.04 s |
| Order by external id / order detail | 0.006 s |
| Item search (50k) | 0.035 s |

Timings can be higher right after the load, before autovacuum has processed the new rows (an
earlier measurement gave ~0.3 s for the unfiltered list, mostly the total `count(*)`). If that matters, the next step is an estimated count or a
"load more" (no total) list. It wasn't done, to keep normal pagination.

## AI usage

See [`docs/ai-usage.md`](docs/ai-usage.md).
