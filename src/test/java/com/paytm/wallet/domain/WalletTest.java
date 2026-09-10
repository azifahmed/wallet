package com.paytm.wallet.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WalletTest {

    @Test
    void create_newUser_hasZeroBalanceAndAssignedId() {
        var wallet = Wallet.create("user-abc");
        assertThat(wallet.getBalance()).isEqualTo(0L);
        assertThat(wallet.getUserId()).isEqualTo("user-abc");
        assertThat(wallet.getId()).isNotNull();
    }

    @Test
    void completed_validInputs_setsAmountStatusAndKey() {
        var fromWalletId = java.util.UUID.randomUUID();
        var toWalletId = java.util.UUID.randomUUID();
        var transfer = Transfer.completed(fromWalletId, toWalletId, 5000L, "key-1");
        assertThat(transfer.getAmountPaise()).isEqualTo(5000L);
        assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(transfer.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(transfer.getFromWalletId()).isEqualTo(fromWalletId);
        assertThat(transfer.getToWalletId()).isEqualTo(toWalletId);
    }
}
