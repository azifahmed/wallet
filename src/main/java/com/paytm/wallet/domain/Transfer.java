package com.paytm.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Transfer {

    public enum Status { COMPLETED, DECLINED }

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "from_wallet_id", nullable = false, columnDefinition = "uuid")
    private UUID fromWalletId;

    @Column(name = "to_wallet_id", nullable = false, columnDefinition = "uuid")
    private UUID toWalletId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "transfer_status", nullable = false)
    private Status status;

    @Column(name = "decline_reason", length = 100)
    private String declineReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Transfer completed(UUID fromWalletId, UUID toWalletId,
                                     long amountPaise, String idempotencyKey) {
        var transfer = new Transfer();
        transfer.id = UUID.randomUUID();
        transfer.fromWalletId = fromWalletId;
        transfer.toWalletId = toWalletId;
        transfer.amountPaise = amountPaise;
        transfer.idempotencyKey = idempotencyKey;
        transfer.status = Status.COMPLETED;
        transfer.createdAt = Instant.now();
        return transfer;
    }
}
