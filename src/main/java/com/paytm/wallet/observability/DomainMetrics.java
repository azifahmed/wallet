package com.paytm.wallet.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DomainMetrics {

    private final Counter transfersCreated;
    private final Counter transfersDeclined;
    private final Counter transfersIdempotentReplay;
    private final Counter walletsCreated;

    public DomainMetrics(MeterRegistry registry) {
        this.transfersCreated = Counter.builder("transfers_created_total")
            .description("Transfers successfully completed").register(registry);
        this.transfersDeclined = Counter.builder("transfers_declined_insufficient_funds_total")
            .description("Transfers declined — insufficient funds").register(registry);
        this.transfersIdempotentReplay = Counter.builder("transfers_idempotent_replay_total")
            .description("Idempotent transfer replays served").register(registry);
        this.walletsCreated = Counter.builder("wallets_created_total")
            .description("Wallets created (not replayed)").register(registry);
    }

    public void incrementTransferCreated() { transfersCreated.increment(); }
    public void incrementDeclined() { transfersDeclined.increment(); }
    public void incrementIdempotentReplay() { transfersIdempotentReplay.increment(); }
    public void incrementWalletCreated() { walletsCreated.increment(); }
}
