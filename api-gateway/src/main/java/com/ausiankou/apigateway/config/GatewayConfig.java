package com.ausiankou.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/auth/login", "/api/auth/register", "/api/auth/refresh")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("authService")
                                        .setFallbackUri("forward:/fallback/auth"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("http://localhost:8083"))

                // Logout обрабатывается в самом gateway (не идёт в auth-service)
                .route("auth-logout", r -> r
                        .path("/api/auth/logout", "/api/auth/check-token", "/api/auth/invalidate-all")
                        .uri("no://op"))  // Будет обработан локальным контроллером

                .route("user-service", r -> r
                        .path("/api/users/**", "/api/cards/**", "/api/internal/auth/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("userService")
                                        .setFallbackUri("forward:/fallback/user"))
                                .retry(config -> config
                                        .setRetries(3)))
                        .uri("http://localhost:8082"))

                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("orderService")
                                        .setFallbackUri("forward:/fallback/order"))
                                .retry(config -> config
                                        .setRetries(3)))
                        .uri("http://localhost:8085"))
                .build();
    }
}
