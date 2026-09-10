package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.WalletNotFoundException;
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
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletInsertService walletInsertService;

    @Transactional
    public Wallet getOrCreate(String userId) {
        try {
            return walletInsertService.insertWallet(userId);
        } catch (DataIntegrityViolationException ex) {
            log.debug("event=wallet_get_or_create_conflict user_id={}", userId);
            return walletRepository.findByUserId(userId).orElseThrow(
                () -> new WalletNotFoundException("user:" + userId));
        }
    }

    public Wallet getById(UUID walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId.toString()));
    }
}
