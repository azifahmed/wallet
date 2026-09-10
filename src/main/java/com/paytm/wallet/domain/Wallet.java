package com.paytm.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets",
       uniqueConstraints = @UniqueConstraint(name = "wallets_user_id_unique", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Wallet {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Wallet create(String userId) {
        var wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.userId = userId;
        wallet.balance = 0L;
        wallet.createdAt = Instant.now();
        wallet.updatedAt = Instant.now();
        return wallet;
    }
}
