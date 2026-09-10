package com.paytm.wallet.service;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferIdempotencyRecovery {

    private final TransferRepository transferRepository;
    private final DomainMetrics metrics;

    @Transactional(readOnly = true)
    public Transfer recoverTransfer(
        TransferRequest request,
        DataIntegrityViolationException originalException
    ) {
        Transfer committedTransfer = transferRepository
            .findByIdempotencyKey(request.idempotencyKey())
            .orElseThrow(() -> originalException);
        if (!isSameBody(committedTransfer, request)) {
            log.warn("event=transfer_idempotency_conflict transfer_id={}",
                committedTransfer.getId());
            throw new IdempotencyConflictException(committedTransfer);
        }

        log.info("event=transfer_idempotent_replay transfer_id={}", committedTransfer.getId());
        metrics.incrementIdempotentReplay();
        return committedTransfer;
    }

    private boolean isSameBody(Transfer existingTransfer, TransferRequest request) {
        return existingTransfer.getFromWalletId().equals(request.fromWalletId())
            && existingTransfer.getToWalletId().equals(request.toWalletId())
            && existingTransfer.getAmountPaise() == request.amountPaise();
    }
}
