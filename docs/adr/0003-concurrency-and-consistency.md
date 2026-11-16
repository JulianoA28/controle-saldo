# 0003 — Concurrency, idempotency and consistency

- **Status:** accepted
- **Date:** 2026-11-16

## Order placement (R2, R4)

One transaction per order. Each line is debited with a conditional atomic update:

```sql
UPDATE item SET balance = balance - :q WHERE id = :id AND balance >= :q
```

- The row lock serializes concurrent debits on the same item; after waiting, PostgreSQL
  re-checks the `WHERE` against the committed balance (READ COMMITTED), so overselling is
  impossible without `SELECT ... FOR UPDATE` or SERIALIZABLE retries.
- 0 rows updated → insufficient balance → exception → the whole transaction rolls back (R2).
  All shortages are reported in `details` (item, requested, available).
- Lines are debited **in item id order**. Two orders over the same items lock rows in the
  same sequence, so they can't deadlock. Verified by a test that fails with real deadlocks
  (`CannotAcquireLockException`) when the sort is removed.
- `CHECK (balance >= 0)` is the final guarantee (R1), also against manual SQL.

Rejected alternatives: pessimistic `SELECT ... FOR UPDATE` (more round trips, same
locking); optimistic `@Version` on the balance (constant retries on hot items); a queue
(adds infrastructure without solving anything the row lock doesn't).

## Idempotency (R6)

The client sends `externalId` (UUID): the integration uses its own id; the portal
generates one per order form, so a double click or a retry after a timeout is safe too.

- `UNIQUE (external_id)` is the guarantee. The order row is inserted **before** any
  debit: a concurrent resend blocks on the unique index, and once the first commits it
  gets a unique violation. That is caught, and the existing order is returned with **200**
  (a new order returns **201**). No item is debited twice.
- Same id with **different content** → **409 `IDEMPOTENCY_KEY_REUSED`**. Silently returning
  the old order would hide a client bug.
- A replay returns the order's **current** state (it may have been cancelled since).

## Cancellation (R3)

```sql
UPDATE orders SET status = 'CANCELLED', ... WHERE id = :id AND status = 'CONFIRMED'
```

0 rows → not found (404) or already cancelled (409). Balances are credited in the same
transaction, in item id order (same lock order as placement).

## Two managers editing the same item (R5)

The item has two kinds of data with different concurrency needs:

- **Descriptive fields** (description, unit, active) are edited from a form, after a read.
  They use optimistic locking (`@Version`). The client sends the version it saw. If the item
  changed, the API returns **409 `ITEM_VERSION_CONFLICT`** with the current state, and the screen
  shows "your values vs current values" and lets the user discard or consciously reapply.
- **Balance** is never written from a form. It's mapped `updatable = false`, so Hibernate
  can't write a stale value. It changes only through atomic SQL deltas: order debit,
  cancellation credit, and manual **adjustments** (`POST /items/{id}/adjustments`, logged in
  `item_balance_adjustment` with reason and resulting balance). Deltas commute: two managers
  adding stock at the same time both apply, and nothing is lost.

Why not put the balance under `@Version` too? Every order debit would bump the version, and a
manager editing a description would get conflicts all day on any item that sells. And a
manager typing an absolute balance based on a stale read is exactly the lost update R5
forbids.
