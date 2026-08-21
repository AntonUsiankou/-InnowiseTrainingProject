package com.ausiankou.user.exception;

import org.springframework.http.HttpStatus;

public class UserException extends RuntimeException {
    private final HttpStatus status;

    public UserException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static UserException notFound(String what) {
        return new UserException(what + " not found", HttpStatus.NOT_FOUND);
    }

    public static UserException emailTaken() {
        return new UserException("Email already registered", HttpStatus.CONFLICT);
    }

    public static UserException tooManyCards() {
        return new UserException("A user cannot have more than 5 cards", HttpStatus.BAD_REQUEST);
    }
}
