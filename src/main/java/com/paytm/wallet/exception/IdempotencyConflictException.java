package com.paytm.wallet.exception;

import com.paytm.wallet.domain.Transfer;

public class IdempotencyConflictException extends RuntimeException {
    private final Transfer existingTransfer;

    public IdempotencyConflictException(Transfer existingTransfer) {
        super("Idempotency key reused with different body: " + existingTransfer.getIdempotencyKey());
        this.existingTransfer = existingTransfer;
    }

    public Transfer getExistingTransfer() {
        return existingTransfer;
    }
}
