package com.ausiankou.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {
    private final HttpStatus status;

    public PaymentException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static PaymentException notFound(String what) {
        return new PaymentException(what + " not found", HttpStatus.NOT_FOUND);
    }
}
