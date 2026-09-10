package com.paytm.wallet.web;

import com.paytm.wallet.exception.ForbiddenException;
import com.paytm.wallet.service.WalletService;
import com.paytm.wallet.web.dto.CreateWalletResponse;
import com.paytm.wallet.web.dto.CreditWalletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private static final String ADMIN_USER_ID = "admin";

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

    @PostMapping("/{id}/credit")
    public CreateWalletResponse credit(
            @PathVariable UUID id,
            @Valid @RequestBody CreditWalletRequest request,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (!ADMIN_USER_ID.equals(userId)) {
            throw new ForbiddenException("Admin token required to credit wallets");
        }
        var wallet = walletService.credit(id, request.amount_paise());
        return new CreateWalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }
}
