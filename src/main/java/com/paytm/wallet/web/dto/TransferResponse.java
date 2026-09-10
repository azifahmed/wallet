package com.paytm.wallet.web.dto;

import com.paytm.wallet.domain.Transfer;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID id,
    UUID from,
    UUID to,
    long amountPaise,
    String status,
    String idempotencyKey,
    String declineReason,
    Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
            transfer.getId(),
            transfer.getFromWalletId(),
            transfer.getToWalletId(),
            transfer.getAmountPaise(),
            transfer.getStatus().name(),
            transfer.getIdempotencyKey(),
            transfer.getDeclineReason(),
            transfer.getCreatedAt());
    }
}
