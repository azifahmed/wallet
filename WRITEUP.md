# Wallet & P2P Transfer — Write-Up

## Data Model

**wallets:** UUID PK, unique `user_id` (`VARCHAR(64)`), `balance BIGINT DEFAULT 0`
(`CHECK balance >= 0`), timestamps.

**transfers:** UUID PK, from/to wallet FKs, `amount_paise BIGINT` (`CHECK > 0`), unique
`idempotency_key`, status (`COMPLETED`/`DECLINED`), timestamps. DB check rejects same-wallet
transfers. Money is always integer paise in `BIGINT`/Java `long` — no floats, BigDecimal, or
rupee arithmetic.

## Simplest-Correct Mechanism

No-overdraft is one atomic PostgreSQL statement:

```sql
UPDATE wallets SET balance = balance - :amount WHERE id = :id AND balance >= :amount;
```

Zero affected rows → insufficient funds → rollback. Recipient credit and transfer insert run
in the same `@Transactional` method — debit, credit, and record commit together or not at all.
No read-before-write; the WHERE and SET are atomic, preventing check-then-act overdrafts.

**Rejected:** `SELECT FOR UPDATE` (extra reads, longer locks, ordering); SERIALIZABLE
(serialization failures needing retries); optimistic `@Version` (same under hot wallets).

**Deadlock caveat:** `UPDATE` still holds row locks until commit. Concurrent A→B and B→A can
deadlock at the credit step; Postgres rolls one back, preserving invariants, but the service
has no retry or ordered locking and may return an error.

## Idempotency and Get-or-Create

`transfers.idempotency_key` is UNIQUE. The service checks for an existing row: same key and
body returns it; different body → HTTP 409. The transfer INSERT is in the **same transaction**
as debit and credit — a duplicate cannot commit a second balance movement. Concurrent losers
get `DataIntegrityViolationException`, then a read-only recovery load compares body before
return or 409.

Wallet get-or-create uses the same pattern: `saveAndFlush` in `REQUIRES_NEW` on unique
`user_id`; exactly one concurrent insert wins, losers read the committed wallet.

## Consistency vs Availability

**Chose CP:** single primary Postgres, synchronous reads/writes. DB outage/failover returns
errors rather than stale balances. Gave up zero-downtime failover and geo reads — correct for
money: temporary unavailability is recoverable; stale balances are not.

## Stack and Rate Limiting

Java 21, Spring MVC, JPA/Hibernate, JDBC, PostgreSQL. Virtual threads
(`spring.threads.virtual.enabled=true`) with HikariCP (max 20). Chosen over WebFlux/R2DBC for
simpler blocking transactions at this scale.

In-process **RateLimitStrategy**; default is synchronized **sliding window**. Keys:
`user:<id>` (300 req/60s) after auth, `ip:<addr>` (30 req/60s) on missing/failed auth — above
burst probes, slows token stuffing. Deny → 429 + `Retry-After`; `/actuator/**` excluded. Per-process
state, not coordinated across replicas.

## AI Directed vs Decided

**Directed (I chose; AI typed):** conditional debit SQL, same-tx idempotency boundary,
UNIQUE/`REQUIRES_NEW` get-or-create, schema constraints, virtual threads, Dockerfile structure.

**AI decided (I accepted):** Logback encoder XML, some `xargs -P` burst-script flags, some
DTO record field names.

## Deployment and Cost

Source: <https://github.com/azifahmed/wallet>. `render.yaml` targets free Docker web service
and free Postgres. **Blueprint not yet applied** — no verified live Render URL; apply and verify
health/burst behavior remains. Both on Render `free` plan → **₹0** (cold-start and storage limits apply).
