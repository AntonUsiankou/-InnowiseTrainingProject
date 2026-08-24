package com.ausiankou.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Target of the Resilience4j circuit breaker filters' fallback URIs (see
 * application.yml). When a downstream service's breaker is OPEN, requests
 * are routed here instead of piling up against a dead service - callers get
 * a fast, clear 503 rather than hanging on a timeout for 60s+.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth-service")
    public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
        return fallback("Authentication Service");
    }

    @GetMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback() {
        return fallback("User Service");
    }

    @GetMapping("/order-service")
    public Mono<ResponseEntity<Map<String, Object>>> orderServiceFallback() {
        return fallback("Order Service");
    }

    @GetMapping("/payment-service")
    public Mono<ResponseEntity<Map<String, Object>>> paymentServiceFallback() {
        return fallback("Payment Service");
    }

    private Mono<ResponseEntity<Map<String, Object>>> fallback(String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "message", service + " is temporarily unavailable, please retry shortly"
        )));
    }
}
