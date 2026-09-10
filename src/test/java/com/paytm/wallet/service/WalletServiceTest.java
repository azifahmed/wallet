package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.WalletNotFoundException;
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
    WalletInsertService walletInsertService;
    WalletService walletService;

    @BeforeEach
    void setUp() {
        walletRepository = Mockito.mock(WalletRepository.class);
        walletInsertService = Mockito.mock(WalletInsertService.class);
        walletService = new WalletService(walletRepository, walletInsertService);
    }

    @Test
    void getOrCreate_whenUserHasNone_createsNewWallet() {
        Wallet savedWallet = Wallet.create("user1");
        when(walletInsertService.insertWallet("user1")).thenReturn(savedWallet);

        Wallet result = walletService.getOrCreate("user1");

        assertThat(result.getUserId()).isEqualTo("user1");
        assertThat(result.getBalance()).isEqualTo(0L);
        verify(walletInsertService).insertWallet("user1");
        verify(walletRepository, never()).findByUserId(anyString());
    }

    @Test
    void getOrCreate_whenDuplicateKeyOnSave_returnsExistingWallet() {
        Wallet existingWallet = Wallet.create("user1");
        when(walletInsertService.insertWallet("user1"))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(existingWallet));

        Wallet result = walletService.getOrCreate("user1");

        assertThat(result.getId()).isEqualTo(existingWallet.getId());
        verify(walletRepository).findByUserId("user1");
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
