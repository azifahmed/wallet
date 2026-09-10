package com.paytm.wallet.service;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.exception.TransferNotFoundException;
import com.paytm.wallet.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferTransactionalWriter transactionalWriter;
    private final TransferIdempotencyRecovery idempotencyRecovery;
    private final TransferRepository transferRepository;

    public Transfer transfer(TransferRequest request) {
        validateRequest(request);
        try {
            return transactionalWriter.executeTransfer(request);
        } catch (DataIntegrityViolationException exception) {
            return idempotencyRecovery.recoverTransfer(request, exception);
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

    public Transfer getById(UUID transferId) {
        return transferRepository.findById(transferId)
            .orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
    }
}
