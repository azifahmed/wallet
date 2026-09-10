package com.paytm.wallet.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiErrorControllerTest {

    private ErrorAttributes errorAttributes;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        errorAttributes = mock(ErrorAttributes.class);
        mockMvc = standaloneSetup(new ApiErrorController(errorAttributes)).build();
    }

    @Test
    void error_withUnauthorizedStatus_returnsJsonErrorBody() throws Exception {
        when(errorAttributes.getErrorAttributes(any(WebRequest.class), any(ErrorAttributeOptions.class)))
            .thenReturn(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Missing Bearer token",
                "path", "/wallets"));

        mockMvc.perform(get("/error").requestAttr(
                RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.UNAUTHORIZED.value()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Missing Bearer token"));
    }

    @Test
    void error_withNotFoundStatus_returnsNotFoundJson() throws Exception {
        when(errorAttributes.getErrorAttributes(any(WebRequest.class), any(ErrorAttributeOptions.class)))
            .thenReturn(Map.of(
                "status", 404,
                "error", "Not Found",
                "message", "No message available",
                "path", "/nope"));

        mockMvc.perform(get("/error").requestAttr(
                RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Not Found"));
    }
}
