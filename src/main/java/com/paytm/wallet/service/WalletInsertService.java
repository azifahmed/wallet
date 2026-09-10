package com.paytm.wallet.service;

import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.observability.DomainMetrics;
import com.paytm.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletInsertService {

    private final WalletRepository walletRepository;
    private final DomainMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Wallet insertWallet(String userId) {
        Wallet savedWallet = walletRepository.saveAndFlush(Wallet.newFor(userId));
        log.info("event=wallet_created wallet_id={} user_id={}", savedWallet.getId(), userId);
        metrics.incrementWalletCreated();
        return savedWallet;
    }
}
