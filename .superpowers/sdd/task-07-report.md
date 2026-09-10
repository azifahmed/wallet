# Task 07 Report: WalletService

## Status

DONE_WITH_CONCERNS

## Implementation

- Added `WalletService` with `@Service`, `@Slf4j`, and `@RequiredArgsConstructor`.
- Added transactional `getOrCreate(String userId)` using the database `UNIQUE(user_id)` constraint, `DataIntegrityViolationException` recovery, and a re-select by user ID.
- Added `getById(UUID walletId)` with `WalletNotFoundException` for missing wallets.
- Added structured `event=wallet_created` and `event=wallet_get_or_create_conflict` logs.
- Incremented `wallets_created` only after a successful save.

## TDD Evidence

1. Added the three tests from the task brief.
2. Ran `mvn test -Dtest=WalletServiceTest -q` with the required Java 21 home.
3. Observed the expected RED compilation failure: `cannot find symbol: class WalletService`.
4. Added the minimal `WalletService` implementation.
5. Re-ran the targeted tests and observed exit code 0.

## Verification

- `mvn compile`: BUILD SUCCESS.
- Full `mvn test`: 5 tests run, 0 failures, 0 errors, 0 skipped.
- `WalletServiceTest`: 3 tests run, 0 failures, 0 errors, 0 skipped.
- IDE lint check: no errors.
- `git diff --check`: clean.
- No TODO/FIXME, `System.out`, reactive types, `.block()`, or forbidden money types in the new files.

## Self-review

- The implementation and tests match the task brief exactly.
- The conflict path does not increment `wallets_created`.
- Missing lookups use `Optional.orElseThrow`; no null or reactive flow is introduced.
- Concern: `JpaRepository.save()` may defer the INSERT until transaction flush/commit, so a real duplicate-key exception can occur after `getOrCreate` returns and bypass the method-level catch. If the insert is flushed inside this transaction, Spring may also mark the transaction rollback-only before the re-select. The prescribed unit tests mock an immediate exception and do not exercise this database behavior. A database-backed concurrency integration test and a transaction design that isolates the attempted insert would be needed to prove the race-free goal.

## Important Review Fix

- Added `WalletInsertService.insertWallet(String)` as a separate Spring bean with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
- Changed insertion to `walletRepository.saveAndFlush(...)`, ensuring a `UNIQUE(user_id)` violation is raised inside the isolated transaction.
- Moved `event=wallet_created` logging and `incrementWalletCreated()` into the isolated insert method after `saveAndFlush` succeeds.
- Preserved `WalletService.getOrCreate` recovery: catch `DataIntegrityViolationException`, then re-select with `findByUserId(...).orElseThrow(WalletNotFoundException)`.
- Updated `WalletServiceTest` to mock the insert collaborator and retain the three brief scenarios.
- Added `WalletInsertServiceTest` coverage for flush-before-metrics ordering and no metric on a failed insert.

## Review Fix Verification

- TDD RED: targeted test compilation failed because `WalletInsertService` did not yet exist.
- TDD GREEN: `mvn test -q -Dtest=WalletServiceTest,WalletInsertServiceTest` exited 0.
- Full verification with required Java 21 home: `mvn test -q` exited 0.
- Results: 7 tests run, 0 failures, 0 errors, 0 skipped.
- IDE lint check reported no errors; `git diff --check` passed.
