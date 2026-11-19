# AI usage log

Tool: Claude Code (Claude Opus 5.5). Each entry: what was asked, what was accepted,
what was rejected or corrected, and why.

## 2026-11-16 — Project skeleton

**Asked:** plan and build the skeleton: Spring Boot 2.7 backend, Vue 2 + Vuetify frontend,
docker compose with Postgres 16, Flyway, Testcontainers base test, initial docs.

**Accepted:**
- Package-by-domain layout, Flyway with `ddl-auto=validate`, standard error contract.
- Singleton Testcontainers base class; `@WebMvcTest` for the error handler (no DB needed).
- Vite + `@vitejs/plugin-vue2` instead of Vue CLI; nginx proxying `/api`.

**Rejected / corrected:**
- **Portuguese identifiers** (the AI followed the original convention: `Pedido`, `Orgao`,
  `ErroResponse`, `codigo/mensagem/detalhes`). Rejected: the whole project is now in
  English — code, docs and commits. Already-committed files were renamed and `CLAUDE.md`
  updated.
- **Integration test not running:** first named `ApplicationIT`, which Surefire ignores by
  default, so `./mvnw test` reported green while skipping it. Renamed to
  `ApplicationIntegrationTest` instead of adding Failsafe, to keep one test command.
- **Testcontainers failing on Docker 29** ("client version 1.32 is too old"). Upgrading
  Testcontainers alone did not fix it; pinned `api.version=1.44` in
  `src/test/resources/docker-java.properties`.

## 2026-11-16 — Domain, rules, data load and screens

**Asked:** implement all entities, services, controllers, the legacy data load and the
frontend in one go; the author reviews and refines at the end.

**Accepted:**
- Schema with named constraints as the last guarantee of each rule (ADR 0003).
- Balance outside JPA writes (`updatable = false`) + atomic SQL deltas; `@Version` only for
  descriptive fields. Manual balance changes as logged deltas instead of absolute values.
- Data load as a plpgsql procedure in a Flyway migration, called by a one-shot `psql`
  container after `COPY` (ADR 0002). Legacy `saldo_atual` kept as truth; divergences recorded.
- Concurrency tests released by a latch against real PostgreSQL.

**Rejected / corrected:**
- **Lock-order test validated by mutation:** all tests passed at first, so the sort by item
  id was removed on purpose and the deadlock test confirmed it fails with real PostgreSQL
  deadlocks. Then the sort was restored. A test that never fails proves nothing.
- **First draft of the load procedure** had three issues caught before running it:
  `OR`-combined casts (SQL does not guarantee short-circuit, so `x::int` could run on
  non-numeric input and abort the whole load) → safe-cast functions using `CASE`; a correlated
  `EXISTS` over 2.3M lines without an index → indexed temp tables; casts in `WHERE` clauses
  over rejected rows → typed columns computed once.
- `count(DISTINCT ...) OVER (...)` is not supported by PostgreSQL → replaced with
  `min(x) OVER w <> max(x) OVER w` (only "more than one version?" is needed).
- Optional filters as `(:p IS NULL OR col = :p)` in JPQL were avoided for the 1M-row order
  list (generic plans can't use the index well) → JPA Specifications building only the
  predicates that apply.
- **"Which balance is the truth" was a false dilemma.** The AI's first ADR assumed the
  2,893 divergences were real and argued for keeping `saldo_atual`. The full load then
  reported **0 divergences**, which looked like a bug. Investigating instead of trusting
  either number showed that the 2,893 / 328 figures are exactly what you get by counting the
  duplicated lines of resent orders. Once cleaned, both balances agree. The ADR was
  rewritten with that evidence, and with the check that loading partial orders would make
  331 items diverge (which supports rejecting whole orders).

## 2026-11-17 — Clean-clone verification and autocomplete fix

**Asked:** run the tests and the full stack from a fresh clone, then fix a bug found by
manual testing: on "New order", the Item dropdown showed nothing until something was typed.

**Accepted:**
- Verification: 30/30 tests green; fresh `docker compose up` loads the data in 170 s with the
  numbers in the README; R1/R2/R3/R5/R6 checked through the API on the loaded data.
- Item lines load the first page on focus (the list was only fetched on search-input changes,
  and `hide-no-data` hid the empty menu).

**Rejected / corrected:**
- **`cache-items` + `no-filter` with server-side search** (AI-generated in both item and agency
  autocompletes) shows every result ever loaded, not the current search: after the first page
  loads, searching "caneta" would still list unrelated items. Removed; the selected entry is
  kept in the options by hand so its label stays visible, and the search Vuetify fires when it
  writes the selected label into the box is ignored.

## 2026-11-17 — Full verification from a clean clone

**Asked:** verify everything from scratch against `CLAUDE.md` and the README.

**Accepted (verified):** 30/30 tests on a clean clone; no-cache `docker compose up` (zip and
extracted CSVs, re-run skip, empty `data/`, `.env` ports); every API endpoint and error code;
R1–R6 also under parallel HTTP load; invariants checked in SQL on the loaded data; the UI driven
in a headless browser (including the R5 conflict screen); the lock-order mutation from ADR 0003
reproduced (removing the sort → real PostgreSQL deadlocks).

**Rejected / corrected:**
- **"The 2,893 / 328 divergences come only from duplicated lines" is imprecise.** Recomputed
  independently from the raw CSVs: 2,893 / 328 is the naive sum of every line of confirmed ids,
  which also adds the negative quantities. Removing duplicates alone leaves 707 divergent items;
  excluding unknown-agency orders leaves 331; excluding orders with invalid lines gives 0. The
  conclusion stands, but the docs should give this breakdown instead of "only duplicates".

## 2026-11-19 — Pre-delivery review, README execution guide, merge to master

**Asked:** test everything from zero, review every line of code against the rules, update the
README with everything needed to run the project, and merge to `master` if all is OK.

**Accepted (verified):**
- Full read of backend, migrations, load script, frontend, tests and docs against R1–R6 and the
  `CLAUDE.md` conventions (no entity in controllers, paginated queries, standard errors, English).
- 30/30 tests green. Fresh clone + `docker compose up --build` with only the zip: load in 177 s,
  numbers identical to ADR 0002; second `up` skips the load. Every source row accounted for
  (loaded + quarantined = file rows); invariants checked in SQL on the loaded data.
- Balances re-reconciled from the raw CSVs by an independent Python script (not the SQL
  procedure): 0 divergences after cleaning.
- Every endpoint and error code through nginx; R3/R4/R5/R6 under parallel HTTP requests
  (200 orders on 50 units → 50 × 201, 150 × 422, balance 0; 50 resends → 1 × 201, 49 × 200;
  30 cancels → 1 × 200, 29 × 409; 20 edits on one version → 1 × 200, 19 × 409).
- UI driven in headless Chromium: order list/detail/filter, new order (success and 422),
  R5 conflict between two browser sessions, balance adjustment, data load screen.
- README rewritten as an execution guide (requirements, data placement, services, following the
  load, reset, configuration, troubleshooting, curl examples); performance re-measured.

**Rejected / corrected:**
- **Quantity rule blocked valid orders.** Found by the browser test: retyping the value a field
  already holds (select "1", type "1") leaves Vuetify's internal value as the string `"1"`, so
  `Number.isInteger(v)` failed and "Place order" did nothing while showing 1. Same pattern in the
  item initial balance and adjustment delta rules. The rules now accept numeric strings; the
  payload is unchanged (it reads the `.number`-cast model).
- **Windows checkouts would break the load:** with `core.autocrlf=true`, `load.sh` gets CRLF and
  `/bin/sh` fails on `set -eu\r` (reproduced). Added `.gitattributes` forcing LF for `*.sh`/`mvnw`.
- **The divergence explanation was still "only duplicates"** in ADR 0002, README, `CLAUDE.md` and
  the Data load screen, although the previous entry had found it imprecise. Replaced with the
  breakdown (2,893 → 707 → 331 → 0), re-verified from the raw files.
- `.gitignore` still had Portuguese comments → translated.
- **Not changed (reported to the author for a decision):** an adjustment whose result overflows
  `integer` returns 409 `DATA_INTEGRITY_VIOLATION` (the database still protects the data; a
  `@Max` on `delta` or a 422 would be clearer); Hibernate logs the expected R6 unique violation
  at ERROR level during concurrent resends (noise, not a failure).
