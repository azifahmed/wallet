package com.paytm.wallet.web;

import com.paytm.wallet.exception.ForbiddenException;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.exception.TransferNotFoundException;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.web.dto.ErrorResponse;
import com.paytm.wallet.web.dto.TransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorResponse handleInsufficientFunds(InsufficientFundsException exception) {
        log.warn("event=transfer_declined_insufficient_funds message={}", exception.getMessage());
        return new ErrorResponse("INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler({WalletNotFoundException.class, TransferNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(RuntimeException exception) {
        return new ErrorResponse("NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException exception) {
        log.warn("event=request_forbidden message={}", exception.getMessage());
        return new ErrorResponse("FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public TransferResponse handleIdempotencyConflict(IdempotencyConflictException exception) {
        log.warn("event=idempotency_conflict key={}",
            exception.getExistingTransfer().getIdempotencyKey());
        return TransferResponse.from(exception.getExistingTransfer());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception exception) {
        log.warn("event=request_rejected message={}", exception.getMessage());
        return new ErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception exception) {
        log.error("event=unexpected_error message={}", exception.getMessage(), exception);
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
