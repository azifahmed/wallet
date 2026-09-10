package com.paytm.wallet.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String walletId) {
        super("Insufficient funds in wallet: " + walletId);
    }
}
