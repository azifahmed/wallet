package com.paytm.wallet.concurrency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Caps in-flight DB-bound work to roughly the Hikari pool size so virtual
 * threads do not pile up waiting 30s for a JDBC connection.
 */
@Component
public class DbConcurrencyGate {

    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    @Autowired
    public DbConcurrencyGate(DbConcurrencyProperties properties) {
        this(properties.getPermits(), properties.getAcquireTimeoutMs());
    }

    DbConcurrencyGate(int permits, long acquireTimeoutMs) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1");
        }
        this.semaphore = new Semaphore(permits, true);
        this.acquireTimeoutMs = Math.max(0L, acquireTimeoutMs);
    }

    public boolean tryAcquire() {
        try {
            if (acquireTimeoutMs == 0L) {
                return semaphore.tryAcquire();
            }
            return semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
