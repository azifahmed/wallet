package com.paytm.wallet.web;

import com.paytm.wallet.web.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

/**
 * JSON /error endpoint — replaces Spring Boot Whitelabel HTML for sendError()
 * and unmapped routes.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ApiErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
            webRequest,
            ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE));

        int statusCode = resolveStatus(request, attributes);
        HttpStatus httpStatus = HttpStatus.resolve(statusCode);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            statusCode = httpStatus.value();
        }

        String message = resolveMessage(attributes, httpStatus);
        String errorCode = resolveErrorCode(httpStatus);

        if (statusCode >= 500) {
            log.error("event=http_error status={} path={} message={}",
                statusCode, attributes.get("path"), message);
        } else {
            log.warn("event=http_error status={} path={} message={}",
                statusCode, attributes.get("path"), message);
        }

        return ResponseEntity.status(statusCode).body(new ErrorResponse(errorCode, message));
    }

    private static int resolveStatus(HttpServletRequest request, Map<String, Object> attributes) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusAttr instanceof Integer statusCode) {
            return statusCode;
        }
        Object fromAttributes = attributes.get("status");
        if (fromAttributes instanceof Integer statusCode) {
            return statusCode;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static String resolveMessage(Map<String, Object> attributes, HttpStatus httpStatus) {
        Object message = attributes.get("message");
        if (message instanceof String text && !text.isBlank() && !"No message available".equals(text)) {
            return text;
        }
        Object error = attributes.get("error");
        if (error instanceof String text && !text.isBlank()) {
            return text;
        }
        return httpStatus.getReasonPhrase();
    }

    private static String resolveErrorCode(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case CONFLICT -> "CONFLICT";
            case PAYMENT_REQUIRED -> "INSUFFICIENT_FUNDS";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            default -> httpStatus.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_ERROR";
        };
    }
}
