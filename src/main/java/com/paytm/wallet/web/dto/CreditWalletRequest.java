package com.paytm.wallet.web.dto;

import jakarta.validation.constraints.Positive;

public record CreditWalletRequest(@Positive long amount_paise) {}
