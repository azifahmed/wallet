package com.paytm.wallet.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LiveLogBufferTest {

    private LiveLogBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new LiveLogBuffer(3);
    }

    @Test
    void offer_whenUnderCapacity_keepsAllLinesInOrder() {
        buffer.offer("a");
        buffer.offer("b");

        assertThat(buffer.snapshot()).containsExactly("a", "b");
    }

    @Test
    void offer_whenOverCapacity_evictsOldest() {
        buffer.offer("a");
        buffer.offer("b");
        buffer.offer("c");
        buffer.offer("d");

        assertThat(buffer.snapshot()).containsExactly("b", "c", "d");
    }

    @Test
    void subscribe_receivesNewLines_andUnsubscribeStopsDelivery() throws Exception {
        List<String> received = new ArrayList<>();
        AutoCloseable subscription = buffer.subscribe(received::add);

        buffer.offer("one");
        assertThat(received).containsExactly("one");

        subscription.close();
        buffer.offer("two");
        assertThat(received).containsExactly("one");
    }

    @Test
    void subscribe_whenListenerThrows_removesListener() {
        AtomicReference<RuntimeException> boom = new AtomicReference<>(new RuntimeException("boom"));
        buffer.subscribe(line -> {
            RuntimeException error = boom.getAndSet(null);
            if (error != null) {
                throw error;
            }
        });

        buffer.offer("first");
        buffer.offer("second");

        assertThat(buffer.listenerCount()).isZero();
    }
}
