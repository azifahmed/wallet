package com.paytm.wallet.service;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    WalletRepository walletRepository;
    TransferRepository transferRepository;
    DomainMetrics metrics;
    TransferService transferService;

    UUID fromWalletId = UUID.randomUUID();
    UUID toWalletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        walletRepository = Mockito.mock(WalletRepository.class);
        transferRepository = Mockito.mock(TransferRepository.class);
        metrics = Mockito.mock(DomainMetrics.class);
        var transactionalWriter = new TransferTransactionalWriter(
            walletRepository, transferRepository, metrics);
        var idempotencyRecovery = new TransferIdempotencyRecovery(transferRepository, metrics);
        transferService = new TransferService(
            transactionalWriter, idempotencyRecovery, transferRepository);
    }

    @Test
    void transfer_withSufficientFunds_completesAndReturnsTransfer() {
        stubLockPair(fromWalletId, toWalletId);
        when(transferRepository.findByIdempotencyKey("key1")).thenReturn(Optional.empty());
        when(walletRepository.conditionalDebit(fromWalletId, 5000L)).thenReturn(1);
        when(walletRepository.credit(toWalletId, 5000L)).thenReturn(1);
        Transfer savedTransfer = Transfer.completed(fromWalletId, toWalletId, 5000L, "key1");
        when(transferRepository.saveAndFlush(any())).thenReturn(savedTransfer);

        Transfer result = transferService.transfer(
            new TransferRequest(fromWalletId, toWalletId, 5000L, "key1"));

        assertThat(result.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(result.getAmountPaise()).isEqualTo(5000L);
        var callOrder = Mockito.inOrder(transferRepository, metrics);
        callOrder.verify(transferRepository).saveAndFlush(any());
        callOrder.verify(metrics).incrementTransferCreated();
    }

    @Test
    void transfer_locksWalletsInAscendingUuidOrder_beforeDebitAndCredit() {
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(transferRepository.findByIdempotencyKey("key-order")).thenReturn(Optional.empty());
        when(walletRepository.lockPairForUpdate(smallerId, largerId))
            .thenReturn(List.of(Wallet.create("u1"), Wallet.create("u2")));
        when(walletRepository.conditionalDebit(largerId, 100L)).thenReturn(1);
        when(walletRepository.credit(smallerId, 100L)).thenReturn(1);
        Transfer savedTransfer = Transfer.completed(largerId, smallerId, 100L, "key-order");
        when(transferRepository.saveAndFlush(any())).thenReturn(savedTransfer);

        transferService.transfer(new TransferRequest(largerId, smallerId, 100L, "key-order"));

        var callOrder = inOrder(walletRepository);
        callOrder.verify(walletRepository).lockPairForUpdate(smallerId, largerId);
        callOrder.verify(walletRepository).conditionalDebit(largerId, 100L);
        callOrder.verify(walletRepository).credit(smallerId, 100L);
    }

    @Test
    void transfer_whenConditionalDebitReturnsZero_throwsInsufficientFunds() {
        stubLockPair(fromWalletId, toWalletId);
        when(transferRepository.findByIdempotencyKey("key2")).thenReturn(Optional.empty());
        when(walletRepository.conditionalDebit(fromWalletId, 5000L)).thenReturn(0);

        assertThatThrownBy(() -> transferService.transfer(
            new TransferRequest(fromWalletId, toWalletId, 5000L, "key2")))
            .isInstanceOf(InsufficientFundsException.class);
        verify(metrics).incrementDeclined();
        verify(walletRepository, never()).credit(any(), anyLong());
    }

    @Test
    void transfer_withSameKeyAndSameBody_returnsExistingTransfer() {
        Transfer existingTransfer = Transfer.completed(fromWalletId, toWalletId, 5000L, "key3");
        when(transferRepository.findByIdempotencyKey("key3")).thenReturn(Optional.of(existingTransfer));

        Transfer result = transferService.transfer(
            new TransferRequest(fromWalletId, toWalletId, 5000L, "key3"));

        assertThat(result.getId()).isEqualTo(existingTransfer.getId());
        verify(metrics).incrementIdempotentReplay();
        verify(walletRepository, never()).conditionalDebit(any(), anyLong());
    }

    @Test
    void transfer_withSameKeyAndDifferentBody_throwsIdempotencyConflict() {
        Transfer existingTransfer = Transfer.completed(fromWalletId, toWalletId, 5000L, "key4");
        when(transferRepository.findByIdempotencyKey("key4")).thenReturn(Optional.of(existingTransfer));

        assertThatThrownBy(() -> transferService.transfer(
            new TransferRequest(fromWalletId, toWalletId, 9999L, "key4")))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void transfer_whenConcurrentInsertLosesWithSameBody_recoversCommittedTransfer() {
        Transfer committedTransfer = Transfer.completed(fromWalletId, toWalletId, 5000L, "key5");
        when(transferRepository.findByIdempotencyKey("key5"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(committedTransfer));
        stubLockPair(fromWalletId, toWalletId);
        when(walletRepository.conditionalDebit(fromWalletId, 5000L)).thenReturn(1);
        when(walletRepository.credit(toWalletId, 5000L)).thenReturn(1);
        when(transferRepository.saveAndFlush(any()))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        Transfer result = transferService.transfer(
            new TransferRequest(fromWalletId, toWalletId, 5000L, "key5"));

        assertThat(result.getId()).isEqualTo(committedTransfer.getId());
        verify(metrics).incrementIdempotentReplay();
        verify(metrics, never()).incrementTransferCreated();
    }

    @Test
    void transfer_withSameFromAndTo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> transferService.transfer(
            new TransferRequest(fromWalletId, fromWalletId, 100L, "key-same")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(walletRepository, never()).conditionalDebit(any(), anyLong());
    }

    private void stubLockPair(UUID walletIdA, UUID walletIdB) {
        UUID firstId = walletIdA.compareTo(walletIdB) < 0 ? walletIdA : walletIdB;
        UUID secondId = firstId.equals(walletIdA) ? walletIdB : walletIdA;
        when(walletRepository.lockPairForUpdate(firstId, secondId))
            .thenReturn(List.of(Wallet.create("u1"), Wallet.create("u2")));
    }
}
