package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletServiceTest {

    WalletRepository walletRepository;
    DomainMetrics metrics;
    WalletService walletService;

    @BeforeEach
    void setUp() {
        walletRepository = Mockito.mock(WalletRepository.class);
        metrics = Mockito.mock(DomainMetrics.class);
        walletService = new WalletService(walletRepository, metrics);
    }

    @Test
    void getOrCreate_whenUserHasNone_createsNewWallet() {
        Wallet savedWallet = Wallet.newFor("user1");
        when(walletRepository.save(any())).thenReturn(savedWallet);
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(savedWallet));

        Wallet result = walletService.getOrCreate("user1");

        assertThat(result.getUserId()).isEqualTo("user1");
        assertThat(result.getBalance()).isEqualTo(0L);
        verify(metrics).incrementWalletCreated();
    }

    @Test
    void getOrCreate_whenDuplicateKeyOnSave_returnsExistingWallet() {
        Wallet existingWallet = Wallet.newFor("user1");
        when(walletRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(existingWallet));

        Wallet result = walletService.getOrCreate("user1");

        assertThat(result.getId()).isEqualTo(existingWallet.getId());
        verify(metrics, never()).incrementWalletCreated();
    }

    @Test
    void getById_whenMissing_throwsWalletNotFoundException() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getById(walletId))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining(walletId.toString());
    }
}
