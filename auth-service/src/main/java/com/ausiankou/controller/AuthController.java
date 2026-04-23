package com.ausiankou.controller;

import com.ausiankou.dto.*;
import com.ausiankou.service.AuthenticationServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationServiceImpl authService;

    /**
     * Register a new user
     *
     * @param request registration data
     * @return authentication response with tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate user and generate tokens
     *
     * @param request login credentials
     * @return authentication response with tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Validate JWT token for API Gateway
     * Returns 401 Unauthorized for invalid tokens, 200 OK for valid tokens
     *
     * @param authHeader Authorization header with Bearer token
     * @return validation response with proper HTTP status
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidateTokenResponse> validateToken(@RequestHeader("Authorization") String authHeader) {
        log.info("Token validation request");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ValidateTokenResponse.builder()
                            .valid(false)
                            .message("Invalid Authorization header format")
                            .build());
        }

        String token = authHeader.substring(7);
        ValidateTokenResponse response = authService.validateToken(token);

        if (!response.isValid()) {
            log.warn("Token validation failed: {}", response.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        log.info("Token validation successful for user: {}", response.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token using refresh token
     *
     * @param request refresh token
     * @return new authentication response
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * Logout user by invalidating refresh token
     *
     * @param request refresh token
     * @return empty response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        log.info("Logout request");
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }
}
