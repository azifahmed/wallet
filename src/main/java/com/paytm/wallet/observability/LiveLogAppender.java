package com.paytm.wallet.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.Map;

/**
 * Logback appender that mirrors {@code com.paytm.wallet.*} log lines into {@link LiveLogBuffer}.
 */
public class LiveLogAppender extends AppenderBase<ILoggingEvent> {

    private static final String APP_LOGGER_PREFIX = "com.paytm.wallet";

    private LiveLogBuffer buffer = LiveLogBuffer.getInstance();

    /** Visible for tests. */
    void setBuffer(LiveLogBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || !event.getLoggerName().startsWith(APP_LOGGER_PREFIX)) {
            return;
        }
        buffer.offer(toJsonLine(event));
    }

    static String toJsonLine(ILoggingEvent event) {
        Map<String, String> mdc = readMdc(event);
        String correlationId = mdc.getOrDefault("correlation_id", "");
        String userId = mdc.getOrDefault("user_id", "");
        return "{\"@timestamp\":\"" + Instant.ofEpochMilli(event.getTimeStamp()) + "\""
            + ",\"message\":\"" + escape(event.getFormattedMessage()) + "\""
            + ",\"logger_name\":\"" + escape(event.getLoggerName()) + "\""
            + ",\"level\":\"" + event.getLevel() + "\""
            + ",\"correlation_id\":\"" + escape(correlationId) + "\""
            + ",\"user_id\":\"" + escape(userId) + "\"}";
    }

    private static Map<String, String> readMdc(ILoggingEvent event) {
        try {
            Map<String, String> mdc = event.getMDCPropertyMap();
            return mdc == null ? Map.of() : mdc;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
