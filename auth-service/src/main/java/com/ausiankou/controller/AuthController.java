package com.ausiankou.controller;

import com.ausiankou.dto.*;
import com.ausiankou.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Called by API Gateway login route. Public. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** Used internally by API Gateway / other services to authorize requests. */
    @PostMapping("/token/validate")
    public ResponseEntity<ValidateTokenResponse> validate(@Valid @RequestBody ValidateTokenRequest request) {
        return ResponseEntity.ok(authService.validate(request));
    }

    /** Called internally by API Gateway registration saga to persist credentials. */
    @PostMapping("/credentials")
    public ResponseEntity<UUID> register(@Valid @RequestBody RegisterCredentialsRequest request) {
        UUID id = authService.saveCredentials(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** Compensating (rollback) endpoint used by the gateway saga if the User Service step fails. */
    @DeleteMapping("/credentials/{login}")
    public ResponseEntity<Void> rollback(@PathVariable String login) {
        authService.deleteCredentialsByLogin(login);
        return ResponseEntity.noContent().build();
    }
}
