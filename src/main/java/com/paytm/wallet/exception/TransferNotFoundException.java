package com.paytm.wallet.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String id) {
        super("Transfer not found: " + id);
    }
}
