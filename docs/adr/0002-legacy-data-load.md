# 0002 — Legacy data load

- **Status:** accepted
- **Date:** 2026-11-16

## Context

The legacy files (`orgaos.csv`, `itens.csv`, `pedidos.csv`, `pedido_itens.csv`: UTF-8, `;`,
CRLF line endings, ~3.3M rows in total) have known problems: duplicate order ids, orders from
unknown agencies, orders with no items, lines pointing to unknown items or orders, invalid
quantities, dirty units, and item balances that don't match their order history.
The business rules (R1–R6) must hold for loaded data too.

## Decisions

1. **SQL, not Java, does the load.** `COPY` into `stg_*` tables (all `text`, legacy column
   names), then the `dataload_process()` procedure validates and moves rows. Set-based SQL
   handles millions of rows in seconds; row-by-row JPA would take a long time and would
   need batching tricks. The procedure lives in a Flyway migration (`V3__dataload.sql`), so it
   is versioned with the schema and covered by `DataloadProcedureIntegrationTest`.
2. **One transaction.** The whole load commits or nothing does; a failure leaves an empty
   database, never a half-loaded one. It runs once: `dataload_run` records completion and
   later runs are no-ops.
3. **Nothing is dropped silently.** Every source row that is not loaded goes to
   `dataload_rejection` with file, line number, reason and the original row (`jsonb`).
4. **Rules per file:**
   - Units: `trim + upper` (` un`, `Un` → `UN`). The schema enforces the normalized form.
   - Orders with the same `id_externo` and **identical content** are integration resends
     (R6): the first is loaded, the copies are logged as `DUPLICATE_RESEND`. Same id with
     **different content** would be `CONFLICTING_DUPLICATE` (all versions rejected).
     Identical duplicate lines of resent orders are logged as `DUPLICATE_ROW`.
   - **R2 applies to the legacy data:** an order with any invalid line (unknown item,
     invalid quantity) is rejected **whole** (`INVALID_LINES`). Loading only its valid
     lines would record an order that never existed in that form. Its valid lines are
     logged as `ORDER_REJECTED`.
   - Orders with no lines → `NO_ITEMS`. Lines whose order doesn't exist → `ORDER_NOT_FOUND`.
   - Legacy timestamps have no time zone; they are read as `America/Sao_Paulo` and stored as `timestamptz`.
5. **Which balance is the truth — resolved by the data.** A naive recalculation
   (`saldo_inicial − Σ quantities of every line of a confirmed id`, straight from the CSVs)
   shows **2,893 divergent items, 328 of them negative**. Recomputed independently from the
   raw files, removing one cause at a time:

   | Recalculation | Divergent items |
   |---|---:|
   | Naive (every line, including duplicates and quantities ≤ 0) | 2,893 |
   | Duplicated lines of resent orders counted once | 707 |
   | … and orders from unknown agencies excluded | 331 |
   | … and orders with any invalid line excluded (= what the load does) | **0** |

   Duplicates are the biggest cause, but not the only one: the legacy system also never
   counted orders from unknown agencies, nor orders with an invalid line. After the same
   cleaning the load applies, the recalculation matches the legacy `saldo_atual` for **all
   50,000 items: 0 divergences**. So `saldo_atual` is loaded as the balance, and the two
   candidate sources of truth agree.

   The same check backs decision 4: loading the valid lines of the 420 confirmed orders
   rejected as `INVALID_LINES` (partial orders) would make **331 items** diverge from the
   legacy balance. Rejecting them whole is what the data shows, not just a preference.

   The reconciliation stays in the load as a permanent check: any divergence would be
   recorded in `dataload_divergence` (initial, legacy, confirmed, recalculated) and shown in
   the "Data load" screen, never "fixed" silently. If a future file diverges, the rule is to
   **keep `saldo_atual`**: recalculating would break R1 for items going negative, and keeping
   R1 would require un-confirming orders that were already delivered.
6. **Historical orders don't touch the balance on load** (it's already reflected in
   `saldo_atual`). Cancelling a loaded confirmed order afterwards returns its quantities,
   like any other order.

## Result (full load, ~3 minutes)

| | Loaded | Rejected |
|---|---:|---|
| Agencies | 250 | — |
| Items | 50,000 | — |
| Orders | 999,150 | 2,000 `DUPLICATE_RESEND`, 450 `INVALID_LINES`, 300 `AGENCY_NOT_FOUND`, 100 `NO_ITEMS` |
| Order lines | 2,323,686 | 4,513 `DUPLICATE_ROW`, 1,246 `ORDER_REJECTED`, 250 `ITEM_NOT_FOUND`, 200 `INVALID_QUANTITY`, 150 `ORDER_NOT_FOUND` |
| Balance divergences | 0 | |

## Consequences

- The app starts with clean, rule-abiding data plus an audit trail of everything left out.
- The 450 orders with invalid lines are kept in quarantine with their original rows, for
  the business to decide whether they should be re-entered.
