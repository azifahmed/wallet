package com.paytm.wallet.web;

import com.paytm.wallet.service.WalletService;
import com.paytm.wallet.web.dto.CreateWalletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWalletResponse createOrGet(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        var wallet = walletService.getOrCreate(userId);
        return new CreateWalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }

    @GetMapping("/{id}")
    public CreateWalletResponse getById(@PathVariable UUID id) {
        var wallet = walletService.getById(id);
        return new CreateWalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }
}
