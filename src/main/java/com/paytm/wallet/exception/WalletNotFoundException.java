package com.paytm.wallet.exception;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String id) {
        super("Wallet not found: " + id);
    }
}
