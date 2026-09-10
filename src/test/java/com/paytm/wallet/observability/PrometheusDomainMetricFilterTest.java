package com.paytm.wallet.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusDomainMetricFilterTest {

    private PrometheusDomainMetricFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new PrometheusDomainMetricFilter();
        response = new MockHttpServletResponse();
    }

    @Test
    void doFilterInternal_forPrometheusEndpoint_rewritesMicrometerCounterNames() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");

        String micrometerBody = """
            # HELP transfers_total Transfers successfully completed
            # TYPE transfers_total counter
            transfers_total 1.0
            transfers_declined_insufficient_funds_total 0.0
            transfers_idempotent_replay_total 0.0
            # HELP wallets_total Wallets created
            # TYPE wallets_total counter
            wallets_total 2.0
            """;

        FilterChain chain = (req, res) -> {
            res.setContentType("text/plain;version=0.0.4;charset=utf-8");
            res.getOutputStream().write(micrometerBody.getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilterInternal(request, response, chain);

        String body = response.getContentAsString();
        assertThat(body).contains(
                "transfers_created_total",
                "transfers_declined_insufficient_funds_total",
                "transfers_idempotent_replay_total",
                "wallets_created_total");
        assertThat(body).doesNotContain("transfers_total");
        assertThat(body).doesNotContain("wallets_total");
    }

    @Test
    void doFilterInternal_forOtherEndpoints_doesNotRewriteBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        String originalBody = "{\"status\":\"UP\"}";

        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            res.getOutputStream().write(originalBody.getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo(originalBody);
    }
}
