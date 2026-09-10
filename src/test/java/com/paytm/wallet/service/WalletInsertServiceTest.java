package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletInsertServiceTest {

    WalletRepository walletRepository;
    DomainMetrics metrics;
    WalletInsertService walletInsertService;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        metrics = mock(DomainMetrics.class);
        walletInsertService = new WalletInsertService(walletRepository, metrics);
    }

    @Test
    void insertWallet_whenInsertSucceeds_flushesBeforeIncrementingMetrics() {
        Wallet savedWallet = Wallet.create("user1");
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenReturn(savedWallet);

        Wallet result = walletInsertService.insertWallet("user1");

        assertThat(result).isSameAs(savedWallet);
        InOrder insertThenMetrics = inOrder(walletRepository, metrics);
        insertThenMetrics.verify(walletRepository).saveAndFlush(any(Wallet.class));
        insertThenMetrics.verify(metrics).incrementWalletCreated();
    }

    @Test
    void insertWallet_whenUniqueConstraintFails_doesNotIncrementMetrics() {
        when(walletRepository.saveAndFlush(any(Wallet.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> walletInsertService.insertWallet("user1"))
            .isInstanceOf(DataIntegrityViolationException.class);

        verify(metrics, never()).incrementWalletCreated();
    }
}
