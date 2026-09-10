package com.paytm.wallet.observability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory ring buffer of recent app log lines for the public /logs demo stream.
 * Shared singleton so Logback's {@link LiveLogAppender} (created outside Spring) can publish.
 */
public final class LiveLogBuffer {

    private static final int DEFAULT_CAPACITY = 500;
    private static final LiveLogBuffer INSTANCE = new LiveLogBuffer(DEFAULT_CAPACITY);

    private final int capacity;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final Set<Consumer<String>> listeners = ConcurrentHashMap.newKeySet();

    public LiveLogBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    public static LiveLogBuffer getInstance() {
        return INSTANCE;
    }

    public void offer(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        List<Consumer<String>> toNotify;
        synchronized (this) {
            lines.addLast(line);
            while (lines.size() > capacity) {
                lines.removeFirst();
            }
            toNotify = new ArrayList<>(listeners);
        }
        for (Consumer<String> listener : toNotify) {
            try {
                listener.accept(line);
            } catch (RuntimeException ex) {
                listeners.remove(listener);
            }
        }
    }

    public synchronized List<String> snapshot() {
        return List.copyOf(lines);
    }

    public AutoCloseable subscribe(Consumer<String> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }
}
