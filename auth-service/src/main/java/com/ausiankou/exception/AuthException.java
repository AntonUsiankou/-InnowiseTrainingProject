package com.ausiankou.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AuthException invalidCredentials() {
        return new AuthException("Invalid login or password", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException loginTaken() {
        return new AuthException("Login already registered", HttpStatus.CONFLICT);
    }

    public static AuthException invalidToken() {
        return new AuthException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException userDisabled() {
        return new AuthException("User is deactivated", HttpStatus.FORBIDDEN);
    }
}
