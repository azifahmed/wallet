package com.paytm.wallet.concurrency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fail-fast admission control for DB-bound routes. Virtual threads can accept
 * far more requests than Hikari has connections; without this gate, excess
 * work waits on the pool until {@code connection-timeout} and returns 500.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class DbConcurrencyFilter extends OncePerRequestFilter {

    private final DbConcurrencyProperties properties;
    private final DbConcurrencyGate gate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/logs") || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!gate.tryAcquire()) {
            log.warn("event=db_concurrency_rejected path={} available_permits={}",
                path, gate.availablePermits());
            writeBusyResponse(response, properties.getRetryAfterSeconds());
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            gate.release();
        }
    }

    static void writeBusyResponse(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":\"DB_BUSY\",\"message\":\"Too many concurrent database operations\"}");
    }
}
