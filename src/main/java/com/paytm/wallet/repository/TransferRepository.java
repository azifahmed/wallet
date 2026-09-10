package com.paytm.wallet.repository;

import com.paytm.wallet.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("DELETE FROM Transfer t WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId")
    int deleteAllByWalletId(@Param("walletId") UUID walletId);
}
