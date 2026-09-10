package com.paytm.wallet.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Micrometer 1.13 strips {@code _created} from counter names on export
 * ({@code transfers_created} → {@code transfers_total}). Rewrite scrape output
 * so /actuator/prometheus exposes the Global Constraint counter names.
 */
@Component
@Order(0)
public class PrometheusDomainMetricFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (!"/actuator/prometheus".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapped);

        String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
        body = body.replace("transfers_total", "transfers_created_total")
                   .replace("wallets_total", "wallets_created_total");

        response.setContentType(wrapped.getContentType());
        response.setCharacterEncoding(wrapped.getCharacterEncoding());
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
