# Task 18 Report

## Important finding fix — burst.sh metric names

**Issue:** `burst.sh` was aligned to Micrometer-exported `transfers_total` / `wallets_total` instead of Global Constraint names.

**Fix:**
- Restored `burst.sh` COUNTER loop to: `transfers_created_total`, `transfers_declined_insufficient_funds_total`, `transfers_idempotent_replay_total`, `wallets_created_total`.
- `DomainMetrics` builder names use base names without `_total` (Micrometer appends `_total` on export).
- Added `PrometheusDomainMetricFilter` to rewrite Micrometer 1.13 `_created` stripping (`transfers_created` → `transfers_total`) back to required Global Constraint names on `/actuator/prometheus`.

**BearerAuthFilter / SecurityConfig:** `BearerAuthFilter` remains `@Component` `@Order(2)`; `RateLimitFilter` `@Order(3)`. `SecurityConfig` only exposes `AuthProperties` bean — filter auto-registered by Spring Boot.

**Verification:**
- `mvn test -q` — pass
- `curl /actuator/prometheus` — all four domain counters present with Global Constraint names
