package com.ausiankou.controller;


import com.ausiankou.dto.*;
import com.ausiankou.entity.UserCredentials;
import com.ausiankou.service.AuthServiceImpl;
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

    private final AuthServiceImpl authService;

    /**
     * Register a new user
     *
     * @param request registration data
     * @return authentication response with tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        log.info("REST request to register user: {}", request.getEmail());
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
        log.info("REST request to login user: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
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
        log.info("REST request to validate token");

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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        return ResponseEntity.ok(response);

    }

    /**
     * Refresh access token using refresh token
     *
     * @param request refresh token
     * @return new authentication response
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh token");
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Logout user by invalidating refresh token
     *
     * @param request refresh token
     * @return empty response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        log.info("REST request to logout");
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<UserCredentialsDto> getUserByEmail(@PathVariable String email) {
        log.info("Internal request to get user by email: {}", email);
        var credentials = authService.getUserCredentialsByEmail(email);
        return ResponseEntity.ok(toDto(credentials));
    }

    @GetMapping("/user-id/{userId}")
    public ResponseEntity<UserCredentialsDto> getUserByUserId(@PathVariable Long userId) {
        log.info("Internal request to get user by userId: {}", userId);
        var credentials = authService.getUserCredentialsByUserId(userId);
        return ResponseEntity.ok(toDto(credentials));
    }

    private UserCredentialsDto toDto(UserCredentials credentials) {
        return UserCredentialsDto.builder()
                .email(credentials.getEmail())
                .role(credentials.getRole())
                .userId(credentials.getUserId())
                .enabled(credentials.isEnabled())
                .build();
    }
}
