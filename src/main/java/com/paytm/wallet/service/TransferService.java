package com.paytm.wallet.service;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.exception.TransferNotFoundException;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final DomainMetrics metrics;

    @Transactional
    public Transfer transfer(TransferRequest request) {
        validateRequest(request);

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

        try {
            int rowsAffected = walletRepository.conditionalDebit(
                request.fromWalletId(), request.amountPaise());
            if (rowsAffected == 0) {
                log.warn("event=transfer_declined_insufficient_funds from_wallet={} amount_paise={}",
                    request.fromWalletId(), request.amountPaise());
                metrics.incrementDeclined();
                throw new InsufficientFundsException(request.fromWalletId().toString());
            }

            walletRepository.credit(request.toWalletId(), request.amountPaise());

            Transfer savedTransfer = transferRepository.save(
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
        } catch (DataIntegrityViolationException exception) {
            Transfer committedTransfer = transferRepository
                .findByIdempotencyKey(request.idempotencyKey())
                .orElseThrow(() -> exception);
            if (!isSameBody(committedTransfer, request)) {
                log.warn("event=transfer_idempotency_conflict transfer_id={}",
                    committedTransfer.getId());
                throw new IdempotencyConflictException(committedTransfer);
            }
            log.info("event=transfer_idempotent_replay transfer_id={}", committedTransfer.getId());
            metrics.incrementIdempotentReplay();
            return committedTransfer;
        }
    }

    private void validateRequest(TransferRequest request) {
        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new IllegalArgumentException("from_wallet_id and to_wallet_id must differ");
        }
        if (request.amountPaise() <= 0) {
            throw new IllegalArgumentException("amount_paise must be greater than zero");
        }
    }

    private boolean isSameBody(Transfer existingTransfer, TransferRequest request) {
        return existingTransfer.getFromWalletId().equals(request.fromWalletId())
            && existingTransfer.getToWalletId().equals(request.toWalletId())
            && existingTransfer.getAmountPaise() == request.amountPaise();
    }

    public Transfer getById(UUID transferId) {
        return transferRepository.findById(transferId)
            .orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
    }
}
