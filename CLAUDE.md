# CLAUDE.md — Item balance and order control for public agencies

> Technical challenge (Full Stack, mid-level) for a hiring process. The evaluation weighs
> **decisions and their justification** more than the number of features. All generated
> code must be understood and defended by the author (Juliano).

## Stack (mandatory — do not change)
- Backend: Java 17 + Spring Boot **2.7.x** (javax.*, NOT jakarta.*), Maven
- Frontend: **Vue 2** + **Vuetify 2** (no Vue 3, no Vue 3 Composition API, no Vuetify 3)
- Database: PostgreSQL 16
- Everything starts with `docker compose up` (database, backend, frontend and the data load)
- Schema migrations: Flyway
- Tests: JUnit 5 + Testcontainers (real Postgres; no H2 for concurrency tests)

## Language
**Everything in English**: code, identifiers, file names, DB tables/columns, comments,
docs, commit messages. Only exception: legacy CSV column names (e.g. `id_externo`) are kept
as-is in the staging tables and mapped to English names in the final tables.

Domain glossary: item = item, pedido = **order** (table `orders`, since `ORDER` is reserved),
órgão = **agency**, saldo = **balance**, carga = **data load**.

## Structure
```
backend/    Spring Boot (packages by domain: item, order, agency, dataload, common)
frontend/   Vue 2 + Vuetify
dataload/   load.sh (COPY + CALL dataload_process(); run by the `dataload` compose service)
docs/       decisions (short ADRs), ai-usage.md
data/       legacy CSVs (NOT versioned — in .gitignore)
docker-compose.yml
README.md
```

## Business rules (invariants — also apply to loaded data)
1. Item: unique code, description, unit, available balance. **Balance is never negative.**
2. An order belongs to one agency and has ≥ 1 item. **Fulfilled entirely or not at all.**
3. An order can be cancelled; quantities return to the balance. **Cancelled only once.**
4. Many concurrent users ordering the same items.
5. Two managers editing the same item: **no change is lost without someone knowing.**
6. An integration resends the same request with the same identifier:
   **the same order is never registered twice.**

## How each rule is enforced (current decisions — revisit if they change)
- R1: `CHECK (balance >= 0)` in the database + validation in the service. The constraint is the final guarantee.
- R2/R4: order registration in a single transaction. Atomic debit per item:
  `UPDATE item SET balance = balance - :q WHERE id = :id AND balance >= :q` — if any update
  affects 0 rows, the whole order is rolled back. Items processed **sorted by id** to
  avoid deadlocks between concurrent orders.
- R3: `UPDATE orders SET status='CANCELLED' ... WHERE id = :id AND status='CONFIRMED'`;
  0 rows affected = already cancelled → business error (409). Balance returned in the same transaction.
- R5: optimistic locking (`@Version`) on the Item's descriptive fields; conflict → HTTP 409 with
  the current state; the frontend shows the conflict and lets the user reload/reapply.
  The balance is `updatable = false` in JPA and only changes via atomic SQL deltas
  (order debit/credit, logged manual adjustments) — see ADR 0003.
- R6: `UNIQUE (external_id)`. Resend with the same id → returns the existing order
  (idempotent response), never creates another. Handle the race via the unique violation.
- Messaging and cache: **not used** (justified in the README). Do not add without discussing.

## Legacy data (findings — see README section "Data load")
Files are UTF-8, `;`-separated, with header. Load: `COPY` into `stg_*` tables, cleaning/validation
in SQL, then insert into final tables. Rejected rows go to a quarantine table with a reason.
- 2,000 rows in `pedidos.csv` with repeated `id_externo` (identical rows = resend)
- 300 orders from non-existent agencies; 100 orders without items
- 250 `pedido_itens` rows with non-existent item; 150 with non-existent order
- 200 invalid quantities (≤ 0 or non-numeric)
- Dirty units (` UN`, `un`, `Un`, `Cx`...) → normalize (trim + upper)
- 2,893 items with `saldo_atual` ≠ `saldo_inicial − Σ confirmed orders` and 328 negative —
  **only in a naive sum** (duplicated lines of resent orders, unknown-agency orders and
  orders with invalid lines all counted: 2,893 → 707 → 331 → 0 as each is removed).
  After cleaning: 0 divergences.
- **Decided (ADR 0002):** `saldo_atual` loaded as the balance (it matches the cleaned history);
  reconciliation kept as a check in `dataload_divergence`. Orders with any invalid line are
  quarantined whole (R2) — loading them partially would make 331 items diverge.
- Load logic: `dataload_process()` procedure in Flyway `V3__dataload.sql`; tested by
  `DataloadProcedureIntegrationTest`.

## Conventions
- DTOs separate from entities; never expose JPA entities in controllers
- Business errors via custom exceptions → `@RestControllerAdvice` → standard JSON
  (`code`, `message`, `details`), with consistent HTTP status (400/404/409/422)
- Always paginate queries (1 million orders); no unpaged `findAll()`
- Indexes created in migrations, next to the query that justifies them
- Small commits, imperative mood, in English

## Commands
```bash
docker compose up --build           # start everything
cd backend && ./mvnw test           # backend tests
cd frontend && npm run dev          # frontend in dev mode (:5173, proxies /api to :8080)
```
Ports in Docker: frontend 8081, backend 8080, db 5432. Integration tests need Docker running.
(update as the project evolves)

## AI usage (part of the evaluation)
- At the end of each relevant task, add an entry to `docs/ai-usage.md`:
  date, what was asked, what was accepted, **what was rejected/corrected and why**.
- Before implementing anything non-trivial, propose the plan and wait for approval.
- Do not invent assumptions silently: if a rule is ambiguous, ask or
  record the assumption in `docs/` and in the README.
