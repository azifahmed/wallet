package com.paytm.wallet.web.dto;

import java.util.UUID;

public record CreateWalletResponse(UUID walletId, String userId, long balance) {}
