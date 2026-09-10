package com.paytm.wallet.repository;

import com.paytm.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(String userId);

    /**
     * Locks both wallet rows in ascending primary-key order (ORDER BY id FOR UPDATE).
     * Callers must pass ids already sorted so A→B and B→A take locks in the same order
     * and cannot deadlock. Must run inside the transfer @Transactional.
     */
    @Query(value = "SELECT * FROM wallets WHERE id IN (:firstId, :secondId) ORDER BY id FOR UPDATE",
           nativeQuery = true)
    List<Wallet> lockPairForUpdate(@Param("firstId") UUID firstId, @Param("secondId") UUID secondId);

    /**
     * Atomic conditional debit. Returns 1 if debited, 0 if insufficient funds.
     * Must run inside the same @Transactional as credit() + transfer insert.
     */
    @Modifying
    @Query(value = "UPDATE wallets SET balance = balance - :amount, updated_at = NOW() " +
                   "WHERE id = :id AND balance >= :amount",
           nativeQuery = true)
    int conditionalDebit(@Param("id") UUID id, @Param("amount") long amount);

    @Modifying
    @Query(value = "UPDATE wallets SET balance = balance + :amount, updated_at = NOW() " +
                   "WHERE id = :id",
           nativeQuery = true)
    int credit(@Param("id") UUID id, @Param("amount") long amount);

    @Modifying
    @Query(value = "UPDATE wallets SET balance = 0, updated_at = NOW() WHERE id = :id",
           nativeQuery = true)
    int clearBalance(@Param("id") UUID id);
}
