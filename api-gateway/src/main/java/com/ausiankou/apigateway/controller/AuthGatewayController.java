package com.ausiankou.apigateway.controller;

import com.ausiankou.apigateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthGatewayController {

    private final JwtUtil jwtUtil;

    /**
     * Logout endpoint - добавляет токен в черный список
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Invalid Authorization header")));
        }

        String token = authHeader.substring(7);

        return jwtUtil.blacklistToken(token)
                .then(Mono.just(ResponseEntity
                        .ok(Map.of("message", "Successfully logged out"))))
                .onErrorResume(e -> {
                    log.error("Logout failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Logout failed: " + e.getMessage())));
                });
    }

    /**
     * Invalidate all user sessions (при смене пароля)
     */
    @PostMapping("/invalidate-all")
    public Mono<ResponseEntity<Map<String, String>>> invalidateAllSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Invalid Authorization header")));
        }

        String token = authHeader.substring(7);

        return jwtUtil.extractUserId(token)
                .flatMap(userId -> jwtUtil.blacklistAllUserTokens(userId)
                        .then(Mono.just(ResponseEntity
                                .ok(Map.of("message", "All user sessions invalidated")))))
                .onErrorResume(e -> {
                    log.error("Failed to invalidate sessions: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to invalidate sessions")));
                });
    }

    /**
     * Проверка статуса токена (для отладки)
     */
    @PostMapping("/check-token")
    public Mono<ResponseEntity<Map<String, Object>>> checkToken(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity
                    .badRequest()
                    .body(Map.of("valid", false, "error", "Invalid Authorization header")));
        }

        String token = authHeader.substring(7);

        return jwtUtil.validateToken(token)
                .map(claims -> {
                    Map<String, Object> response = Map.of(
                            "valid", true,
                            "userId", claims.getSubject(),
                            "email", claims.get("email", String.class),
                            "expiration", claims.getExpiration().toString()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity
                        .ok(Map.of("valid", false, "error", e.getMessage()))));
    }
}