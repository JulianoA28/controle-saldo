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
