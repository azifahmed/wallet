package com.paytm.wallet.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveLogAppenderTest {

    private LiveLogBuffer buffer;
    private LiveLogAppender appender;
    private LoggerContext context;

    @BeforeEach
    void setUp() {
        buffer = new LiveLogBuffer(20);
        context = new LoggerContext();
        appender = new LiveLogAppender();
        appender.setContext(context);
        appender.setBuffer(buffer);
        appender.start();
    }

    @Test
    void append_forAppLogger_offersEncodedLine() {
        Logger logger = context.getLogger("com.paytm.wallet.service.TransferService");
        LoggingEvent event = new LoggingEvent(
            Logger.class.getName(),
            logger,
            Level.INFO,
            "event=transfer_completed transfer_id=abc",
            null,
            null);

        appender.append(event);

        assertThat(buffer.snapshot()).hasSize(1);
        assertThat(buffer.snapshot().getFirst()).contains("transfer_completed");
        assertThat(buffer.snapshot().getFirst()).contains("correlation_id");
    }

    @Test
    void append_forFrameworkLogger_ignores() {
        Logger logger = context.getLogger("org.springframework.web.servlet.DispatcherServlet");
        LoggingEvent event = new LoggingEvent(
            Logger.class.getName(),
            logger,
            Level.INFO,
            "Completed 200 OK",
            null,
            null);

        appender.append(event);

        assertThat(buffer.snapshot()).isEmpty();
    }
}
