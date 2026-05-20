package com.ausiankou.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        log.warn("Auth service is unavailable - returning fallback response");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Authentication service is temporarily unavailable",
                        "status", "503",
                        "timestamp", LocalDateTime.now().toString(),
                        "message", "Please try again later"
                )));
    }

    @GetMapping("/user")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback() {
        log.warn("User service is unavailable - returning fallback response");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "User service is temporarily unavailable",
                        "status", "503",
                        "timestamp", LocalDateTime.now().toString(),
                        "message", "User data cannot be retrieved at this moment"
                )));
    }

    @GetMapping("/order")
    public Mono<ResponseEntity<Map<String, Object>>> orderFallback() {
        log.warn("Order service is unavailable - returning fallback response");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Order service is temporarily unavailable",
                        "status", "503",
                        "timestamp", LocalDateTime.now().toString(),
                        "message", "Order operations are currently unavailable"
                )));
    }
}
