# Wallet & P2P Transfer — Write-Up

**Live API:** https://wallet-transfer-mleu.onrender.com · **Repo:** https://github.com/azifahmed/wallet  
**Logs (public):** https://wallet-transfer-mleu.onrender.com/logs · **Metrics:** `GET /actuator/prometheus`  
**Burst:** `bash burst.sh` (or `bash burst.sh http://localhost:8080` after `docker compose up -d --build`)

Money is always integer **paise** (`BIGINT` / Java `long`) — never floats, `BigDecimal`, or rupees-as-decimal.

## Data model

**wallets:** `id UUID PK`, `user_id VARCHAR(64) UNIQUE NOT NULL`, `balance BIGINT DEFAULT 0` with `CHECK (balance >= 0)`, timestamps. Unique `user_id` makes get-or-create race-free: one concurrent insert wins; losers hit the constraint and `SELECT` the same row.

**transfers:** `id UUID PK`, from/to wallet FKs, `amount_paise BIGINT CHECK > 0`, `idempotency_key VARCHAR(255) UNIQUE NOT NULL`, `status` (`COMPLETED`/`DECLINED`), `CHECK (from ≠ to)`, timestamps.

Admin credit/clear/delete only seed or reset demos (they mint or wipe). **Conservation applies to `POST /transfers`**, not to admin mint.

## Simplest-correct mechanism

One `@Transactional` method: lock both wallets → conditional debit → credit → insert transfer. All commit or none do. Sum of balances is unchanged on a completed transfer; a declined debit leaves no partial apply.

No-overdraft is one atomic statement, not a Java read-then-write:

```sql
UPDATE wallets SET balance = balance - :amount
 WHERE id = :id AND balance >= :amount;
```

Zero rows → insufficient funds → rollback. Credit is `balance = balance + :amount` in the same transaction.

**Deadlock:** `UPDATE` holds row locks until commit. Debit-from then credit-to deadlocks when A→B and B→A run together. Before money moves we lock both rows in a total order:

```sql
SELECT * FROM wallets WHERE id IN (:first, :second) ORDER BY id FOR UPDATE
```

Java sorts the two UUIDs first, so opposite-direction transfers lock `min(id)` then `max(id)`. No wait cycle. Missing wallet → 404 before a debit.

**Why this pair.** Conditional `UPDATE … WHERE balance >= amount` is the smallest correct no-overdraft gate (check and subtract are one statement). Sorted `FOR UPDATE` is the smallest correct deadlock fix, then a directional debit/credit that is easy to read. No retry loop on the happy path.

**Rejected:** SERIALIZABLE (serialization failures + retry under A↔B load); optimistic `@Version` (same retry storm on a hot wallet); conditional `UPDATE` *without* lock order (invariants hold because Postgres aborts a waiter, but the API can 500); lock-then-subtract in Java (check-then-act unless the `WHERE` remains the gate).

## Where idempotency lives

Uniqueness is `transfers.idempotency_key` in Postgres. The insert is committed **in the same transaction** as debit and credit.

Existing key + same body → original result, no second debit. Same key + different body → **409**, no ledger movement. Two concurrent first-timers can both miss the pre-check `SELECT`; only one `INSERT` wins. The loser’s unique-constraint error rolls back that loser’s debit/credit; recovery re-reads the winner (replay or 409).

A key check in a *separate* transaction would let both callers debit. Same-transaction uniqueness closes that window across retries, crashes, and extra instances.

## Consistency vs availability

**Strong consistency (CP):** one primary Postgres; every balance read and transfer write is synchronous. Database down → errors, not a stale replica/cache. **Gave up** availability through failover and geo reads. A stale balance is a fraud event; a brief 5xx is recoverable. Rate limiting is in-process (sliding window, user/IP) — enough for one free-tier instance.

## AI directed vs decided

**Directed (I implemented; AI helped in typing the fill ins):** integer paise; unique `user_id` get-or-create; unique `idempotency_key` in the same txn as the ledger; conditional debit SQL; sorted `FOR UPDATE` after A↔B deadlocks; Dockerfile (multi-stage, non-root, `HEALTHCHECK`); public `/logs` SSE.

**AI decided (I accepted):** Logback JSON encoder XML, some `xargs -P` flags in `burst.sh`, some DTO field names.

## Free-tier cost

Render free Docker web service + free managed Postgres (`render.yaml`). Local compose is app + Postgres, one command. **₹0**, no card, no paid add-ons. The web service sleeps when idle (cold-start before a live probe); free Postgres has storage/expiry limits.
