package com.paytm.wallet.service;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferTransactionalWriter {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final DomainMetrics metrics;

    @Transactional
    public Transfer executeTransfer(TransferRequest request) {
        var existingOptional = transferRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOptional.isPresent()) {
            Transfer existingTransfer = existingOptional.get();
            if (isSameBody(existingTransfer, request)) {
                log.info("event=transfer_idempotent_replay transfer_id={}", existingTransfer.getId());
                metrics.incrementIdempotentReplay();
                return existingTransfer;
            }
            log.warn("event=transfer_idempotency_conflict transfer_id={}", existingTransfer.getId());
            throw new IdempotencyConflictException(existingTransfer);
        }

        walletRepository.findById(request.fromWalletId())
            .orElseThrow(() -> new WalletNotFoundException(request.fromWalletId().toString()));
        walletRepository.findById(request.toWalletId())
            .orElseThrow(() -> new WalletNotFoundException(request.toWalletId().toString()));

        int rowsAffected = walletRepository.conditionalDebit(
            request.fromWalletId(), request.amountPaise());
        if (rowsAffected == 0) {
            log.warn("event=transfer_declined_insufficient_funds from_wallet={} amount_paise={}",
                request.fromWalletId(), request.amountPaise());
            metrics.incrementDeclined();
            throw new InsufficientFundsException(request.fromWalletId().toString());
        }
        log.info("event=transfer_debited from_wallet={} amount_paise={}",
            request.fromWalletId(), request.amountPaise());

        walletRepository.credit(request.toWalletId(), request.amountPaise());
        log.info("event=transfer_credited to_wallet={} amount_paise={}",
            request.toWalletId(), request.amountPaise());

        Transfer savedTransfer = transferRepository.saveAndFlush(
            Transfer.completed(
                request.fromWalletId(),
                request.toWalletId(),
                request.amountPaise(),
                request.idempotencyKey()));

        log.info("event=transfer_completed transfer_id={} from_wallet={} to_wallet={} amount_paise={}",
            savedTransfer.getId(), request.fromWalletId(), request.toWalletId(),
            request.amountPaise());
        metrics.incrementTransferCreated();
        return savedTransfer;
    }

    private boolean isSameBody(Transfer existingTransfer, TransferRequest request) {
        return existingTransfer.getFromWalletId().equals(request.fromWalletId())
            && existingTransfer.getToWalletId().equals(request.toWalletId())
            && existingTransfer.getAmountPaise() == request.amountPaise();
    }
}
