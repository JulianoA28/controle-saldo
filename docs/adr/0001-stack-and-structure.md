# 0001 — Stack and project structure

- **Status:** accepted
- **Date:** 2026-11-16

## Context

The challenge mandates Java 17 + Spring Boot 2.7, Vue 2 + Vuetify 2, PostgreSQL 16, and
a single `docker compose up` to run everything. Both Spring Boot 2.7 and Vue 2 are past
end of open-source support; that is a constraint of the challenge, not a choice.

## Decisions

1. **Spring Boot 2.7.18** (last 2.7 release), `javax.*` APIs. Packages by domain
   (`item`, `order`, `agency`, `dataload`, `common`) rather than by layer, so each rule's
   code lives together.
2. **Flyway owns the schema**; Hibernate runs with `ddl-auto=validate`. Constraints that
   guarantee business rules (e.g. `CHECK (balance >= 0)`) live in migrations, where they
   are reviewable and also protect the data load, which bypasses JPA.
3. **Standard error contract** `{ code, message, details }` via `@RestControllerAdvice`.
   Business exceptions carry their HTTP status: 404 not found, 409 conflict with current
   state, 422 business rule violation, 400 malformed/invalid input, 500 without internals.
4. **Testcontainers with a singleton PostgreSQL 16 container** shared by all integration
   tests. H2 is ruled out: concurrency and constraint behavior must match production.
   `docker-java.properties` pins API version 1.44 because Docker Engine 29 rejects the
   default 1.32.
5. **Vite instead of Vue CLI** for Vue 2.7 (`@vitejs/plugin-vue2`). Vue CLI is in
   maintenance mode and slower; Vite supports Vue 2.7 officially via the plugin.
6. **Vuetify prebuilt CSS** instead of SASS sources + tree-shaking loader. Avoids the legacy
   sass 1.32 toolchain. Trade-off: larger bundle (~0.5 MB), irrelevant for an internal tool.
7. **nginx serves the SPA and proxies `/api` to the backend.** Same origin in Docker and
   in dev (Vite proxy), so no CORS configuration is needed.
8. **All code, docs and commits in English.**

## Consequences

- Upgrading to Spring Boot 3 / Vue 3 later means `javax` → `jakarta` and a Vuetify 3
  migration; isolated to framework edges because DTOs/services don't depend on them.
- Integration tests need a running Docker daemon.
