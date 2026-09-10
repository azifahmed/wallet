package com.paytm.wallet.service;

import java.util.UUID;

public record TransferRequest(
    UUID fromWalletId,
    UUID toWalletId,
    long amountPaise,
    String idempotencyKey
) {}
