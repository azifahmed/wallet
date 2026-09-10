package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.repository.TransferRepository;
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
    TransferRepository transferRepository;
    WalletInsertService walletInsertService;
    WalletService walletService;

    @BeforeEach
    void setUp() {
        walletRepository = Mockito.mock(WalletRepository.class);
        transferRepository = Mockito.mock(TransferRepository.class);
        walletInsertService = Mockito.mock(WalletInsertService.class);
        walletService = new WalletService(walletRepository, transferRepository, walletInsertService);
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

    @Test
    void credit_withExistingWallet_addsAmountAndReturnsUpdatedWallet() {
        Wallet wallet = Wallet.create("user1");
        when(walletRepository.findById(wallet.getId()))
            .thenReturn(Optional.of(wallet))
            .thenReturn(Optional.of(wallet));
        when(walletRepository.credit(wallet.getId(), 1000000L)).thenReturn(1);

        Wallet result = walletService.credit(wallet.getId(), 1000000L);

        assertThat(result.getId()).isEqualTo(wallet.getId());
        verify(walletRepository).credit(wallet.getId(), 1000000L);
    }

    @Test
    void credit_whenWalletMissing_throwsWalletNotFoundException() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.credit(walletId, 500L))
            .isInstanceOf(WalletNotFoundException.class);
        verify(walletRepository, never()).credit(any(), anyLong());
    }

    @Test
    void clear_withExistingWallet_zerosBalanceAndReturnsUpdatedWallet() {
        Wallet wallet = Wallet.create("user1");
        when(walletRepository.findById(wallet.getId()))
            .thenReturn(Optional.of(wallet))
            .thenReturn(Optional.of(wallet));
        when(walletRepository.clearBalance(wallet.getId())).thenReturn(1);

        Wallet result = walletService.clear(wallet.getId());

        assertThat(result.getId()).isEqualTo(wallet.getId());
        verify(walletRepository).clearBalance(wallet.getId());
    }

    @Test
    void clear_whenWalletMissing_throwsWalletNotFoundException() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.clear(walletId))
            .isInstanceOf(WalletNotFoundException.class);
        verify(walletRepository, never()).clearBalance(any());
    }

    @Test
    void delete_withExistingWallet_removesTransfersThenWallet() {
        Wallet wallet = Wallet.create("user1");
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(transferRepository.deleteAllByWalletId(wallet.getId())).thenReturn(3);

        walletService.delete(wallet.getId());

        var callOrder = inOrder(transferRepository, walletRepository);
        callOrder.verify(transferRepository).deleteAllByWalletId(wallet.getId());
        callOrder.verify(walletRepository).deleteById(wallet.getId());
    }

    @Test
    void delete_whenWalletMissing_throwsWalletNotFoundException() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.delete(walletId))
            .isInstanceOf(WalletNotFoundException.class);
        verify(transferRepository, never()).deleteAllByWalletId(any());
        verify(walletRepository, never()).deleteById(any());
    }
}
