package com.ausiankou.orders.exception;

import org.springframework.http.HttpStatus;

public class OrderException extends RuntimeException {
    private final HttpStatus status;

    public OrderException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static OrderException notFound(String what) {
        return new OrderException(what + " not found", HttpStatus.NOT_FOUND);
    }
}
