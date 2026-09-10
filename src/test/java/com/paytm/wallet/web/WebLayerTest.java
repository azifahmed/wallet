package com.paytm.wallet.web;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.exception.TransferNotFoundException;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.service.TransferRequest;
import com.paytm.wallet.service.TransferService;
import com.paytm.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WebLayerTest {

    private WalletService walletService;
    private TransferService transferService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        transferService = mock(TransferService.class);
        mockMvc = standaloneSetup(
            new WalletController(walletService),
            new TransferController(transferService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createOrGet_withAuthenticatedUser_returnsCreatedWallet() throws Exception {
        Wallet wallet = Wallet.create("user-1");
        when(walletService.getOrCreate("user-1")).thenReturn(wallet);

        mockMvc.perform(post("/wallets").requestAttr("userId", "user-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()))
            .andExpect(jsonPath("$.userId").value("user-1"))
            .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void create_withValidBody_delegatesMappedRequestAndReturnsCreatedTransfer() throws Exception {
        UUID fromWalletId = UUID.randomUUID();
        UUID toWalletId = UUID.randomUUID();
        Transfer transfer = Transfer.completed(fromWalletId, toWalletId, 500L, "key-1");
        when(transferService.transfer(org.mockito.ArgumentMatchers.any())).thenReturn(transfer);

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "from": "%s",
                      "to": "%s",
                      "amount_paise": 500,
                      "idempotency_key": "key-1"
                    }
                    """.formatted(fromWalletId, toWalletId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(transfer.getId().toString()));

        ArgumentCaptor<TransferRequest> requestCaptor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService).transfer(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
            .isEqualTo(new TransferRequest(fromWalletId, toWalletId, 500L, "key-1"));
    }

    @Test
    void getById_withExistingResources_returnsMappedResponses() throws Exception {
        Wallet wallet = Wallet.create("user-1");
        Transfer transfer = Transfer.completed(
            wallet.getId(), UUID.randomUUID(), 500L, "key-1");
        when(walletService.getById(wallet.getId())).thenReturn(wallet);
        when(transferService.getById(transfer.getId())).thenReturn(transfer);

        mockMvc.perform(get("/wallets/{id}", wallet.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()));
        mockMvc.perform(get("/transfers/{id}", transfer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(transfer.getId().toString()));
    }

    @Test
    void create_withZeroAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "from": "%s",
                      "to": "%s",
                      "amount_paise": 0,
                      "idempotency_key": "key-1"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void endpoints_whenDomainExceptionsOccur_returnSpecifiedStatuses() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(walletService.getById(walletId)).thenThrow(new WalletNotFoundException(walletId.toString()));
        when(transferService.getById(transferId)).thenThrow(new TransferNotFoundException(transferId.toString()));
        when(transferService.transfer(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new InsufficientFundsException(walletId.toString()));

        mockMvc.perform(get("/wallets/{id}", walletId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(get("/transfers/{id}", transferId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "from": "%s",
                      "to": "%s",
                      "amount_paise": 500,
                      "idempotency_key": "key-1"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID())))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void create_withIdempotencyConflict_returnsExistingTransfer() throws Exception {
        Transfer transfer = Transfer.completed(UUID.randomUUID(), UUID.randomUUID(), 500L, "key-1");
        when(transferService.transfer(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IdempotencyConflictException(transfer));

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "from": "%s",
                      "to": "%s",
                      "amount_paise": 500,
                      "idempotency_key": "key-1"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.id").value(transfer.getId().toString()));
    }

    @Test
    void credit_withAdminUser_returnsUpdatedWallet() throws Exception {
        Wallet wallet = Wallet.create("user-1");
        when(walletService.credit(wallet.getId(), 1000000L)).thenReturn(wallet);

        mockMvc.perform(post("/wallets/{id}/credit", wallet.getId())
                .requestAttr("userId", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount_paise\":1000000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()))
            .andExpect(jsonPath("$.userId").value("user-1"));

        verify(walletService).credit(wallet.getId(), 1000000L);
    }

    @Test
    void credit_withNonAdminUser_returnsForbidden() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(post("/wallets/{id}/credit", walletId)
                .requestAttr("userId", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount_paise\":1000000}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void credit_whenWalletMissing_returnsNotFound() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletService.credit(walletId, 500L))
            .thenThrow(new WalletNotFoundException(walletId.toString()));

        mockMvc.perform(post("/wallets/{id}/credit", walletId)
                .requestAttr("userId", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount_paise\":500}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void clear_withAdminUser_returnsZeroedWallet() throws Exception {
        Wallet wallet = Wallet.create("user-1");
        when(walletService.clear(wallet.getId())).thenReturn(wallet);

        mockMvc.perform(post("/wallets/{id}/clear", wallet.getId())
                .requestAttr("userId", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()));

        verify(walletService).clear(wallet.getId());
    }

    @Test
    void clear_withNonAdminUser_returnsForbidden() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(post("/wallets/{id}/clear", walletId)
                .requestAttr("userId", "user-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(walletService, never()).clear(walletId);
    }

    @Test
    void delete_withAdminUser_returnsNoContent() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(delete("/wallets/{id}", walletId)
                .requestAttr("userId", "admin"))
            .andExpect(status().isNoContent());

        verify(walletService).delete(walletId);
    }

    @Test
    void delete_withNonAdminUser_returnsForbidden() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(delete("/wallets/{id}", walletId)
                .requestAttr("userId", "user-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(walletService, never()).delete(walletId);
    }

    @Test
    void endpoint_whenUnexpectedExceptionOccurs_returnsSanitizedInternalError() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletService.getById(walletId)).thenThrow(new RuntimeException("database password leaked"));

        mockMvc.perform(get("/wallets/{id}", walletId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
