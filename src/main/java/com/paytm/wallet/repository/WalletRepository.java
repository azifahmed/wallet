package com.paytm.wallet.repository;

import com.paytm.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(String userId);

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
}
