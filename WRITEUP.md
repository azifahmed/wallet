# Wallet & P2P Transfer — Write-Up

## Data Model

`wallets` stores a UUID primary key, unique `user_id` (`VARCHAR(64)`), `balance`
(`BIGINT`, default 0, checked non-negative), and creation/update timestamps.
`transfers` stores a UUID primary key, sender and recipient wallet foreign keys,
`amount_paise` (`BIGINT`, checked positive), a unique `idempotency_key`
(`VARCHAR(255)`), PostgreSQL status enum (`COMPLETED`/`DECLINED`), optional decline
reason, and creation timestamp. A database check also rejects transfers to the same
wallet. Money is always integer paise in `BIGINT`/Java `long`; the service does not
use floating-point or decimal rupee arithmetic.

## Simplest-Correct Mechanism

No-overdraft is enforced by one atomic PostgreSQL statement:

```sql
UPDATE wallets
SET balance = balance - :amount
WHERE id = :id AND balance >= :amount;
```

Zero affected rows means insufficient funds and causes the transaction to roll
back. A second `UPDATE` credits the recipient, then the transfer is inserted. All
three writes run in one `@Transactional` method, so debit, credit, and transfer
record commit together or not at all. This is simpler than reading a balance and
then writing it: the predicate and subtraction are evaluated atomically in the
database, preventing check-then-act overdrafts and lost updates.

I rejected `SELECT FOR UPDATE` with sorted wallet IDs because it needs extra reads,
explicit ordering, and longer lock ownership; `SERIALIZABLE` because expected
serialization failures require retry policy; and optimistic locking with
`@Version` because hot wallets similarly turn contention into application retries.

PostgreSQL `UPDATE` still takes row locks until transaction completion. Therefore,
an A→B transaction that has debited A and a B→A transaction that has debited B can
deadlock when each credits the other row. PostgreSQL detects the cycle and rolls
one transaction back, preserving conservation and non-negative balances, but the
current service has no retry or globally ordered locking and may return an error.
The conditional debit is the simplest mechanism that protects the financial
invariants; it is not a claim of deadlock elimination.

## Idempotency and Get-or-Create

Idempotency ultimately lives in PostgreSQL's unique constraint on
`transfers.idempotency_key`. The service first checks for an existing transfer:
same key and same sender, recipient, and amount returns that transfer; a different
body returns HTTP 409. If concurrent requests both pass the check, only one
`saveAndFlush` can win. Its transfer insert is in the same transaction as the
debit and credit. The loser gets `DataIntegrityViolationException` after its
transaction rolls back, then a separate read-only recovery service loads the
winner; it again compares the body before returning it or raising 409. Thus a
duplicate cannot commit a second balance movement.

Wallet get-or-create uses the same database-first idea. Each request attempts a
`saveAndFlush` in `REQUIRES_NEW`; `wallets.user_id` is unique, so exactly one
concurrent insert commits. A loser catches the constraint violation outside that
inner transaction and reads the already committed wallet.

## Consistency vs Availability

The service chooses strong transactional consistency on one primary PostgreSQL
database. It does not serve stale balances or accept writes while the database is
unavailable. This gives up availability during database outages/failover and does
not provide geographically distributed reads, an appropriate trade-off for money:
temporary errors are recoverable, while accepted writes based on stale balances
can violate financial expectations.

## Stack and Rate Limiting

The implementation uses Java 21, Spring MVC, JPA/Hibernate, JDBC, and PostgreSQL.
`spring.threads.virtual.enabled=true` lets blocking request work run on virtual
threads; JDBC concurrency remains bounded by HikariCP (maximum 20 connections).
This keeps ordinary blocking transactions explicit and avoids the complexity of
WebFlux/R2DBC for this service.

Rate limiting is behind a `RateLimitStrategy`; the default implementation is an
in-process, synchronized sliding window. Authenticated requests use `user:<id>`
keys at 300 requests/60 seconds, while missing or failed authentication uses
`ip:<address>` at 30 requests/60 seconds. Rejections return 429 with
`Retry-After`; actuator endpoints are excluded. These limits tolerate the supplied
authenticated burst probes while slowing unauthenticated token attempts. State is
per process, so limits are not coordinated across multiple replicas.

## AI Directed vs Decided

**Directed (I chose the approach; AI assisted with implementation):** conditional
debit SQL, the transaction boundary joining balance writes and idempotency insert,
the unique-constraint/`REQUIRES_NEW` wallet creation pattern, schema constraints,
virtual-thread configuration, and the multi-stage non-root container structure.

**AI decided (suggestions I accepted):** Logback encoder XML details, several
`xargs -P` burst-script flags, and some DTO record field names. I reviewed generated
code and retained responsibility for the design and claims in this write-up.

## Deployment and Cost

The public source is at <https://github.com/azifahmed/wallet>. `render.yaml` is
ready to create a free Docker web service and free managed PostgreSQL database.
The Blueprint has not yet been applied, so there is no verified live Render URL;
applying it and verifying health/burst behavior is the remaining deployment step.
Both resources request Render's `free` plan, so the configured exercise cost is
**₹0**; free services may have lifecycle, storage, and cold-start limitations.
