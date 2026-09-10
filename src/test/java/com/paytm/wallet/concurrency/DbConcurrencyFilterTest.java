package com.paytm.wallet.concurrency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DbConcurrencyFilterTest {

    private DbConcurrencyProperties properties;
    private DbConcurrencyGate gate;
    private DbConcurrencyFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        properties = new DbConcurrencyProperties();
        properties.setEnabled(true);
        properties.setPermits(1);
        properties.setAcquireTimeoutMs(0L);
        gate = new DbConcurrencyGate(properties.getPermits(), properties.getAcquireTimeoutMs());
        filter = new DbConcurrencyFilter(properties, gate);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilter_whenPermitAvailable_passesThroughAndReleases() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/wallets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(gate.availablePermits()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_whenSaturated_returns503WithoutCallingChain() throws Exception {
        assertThat(gate.tryAcquire()).isTrue();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("DB_BUSY");
        assertThat(gate.availablePermits()).isEqualTo(0);
    }

    @Test
    void doFilter_skipsActuatorPaths() throws Exception {
        assertThat(gate.tryAcquire()).isTrue();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_whenDisabled_passesThroughWithoutAcquiring() throws Exception {
        properties.setEnabled(false);
        assertThat(gate.tryAcquire()).isTrue();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
