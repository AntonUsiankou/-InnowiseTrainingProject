package com.ausiankou.apigateway.filter;

import com.ausiankou.apigateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Task: "Add security filter logic to check JWT token on all endpoints
 * except Login and Register".
 *
 * Runs before routing (Ordered.HIGHEST_PRECEDENCE). Public paths pass
 * straight through; everything else must carry a valid Bearer token, which
 * is then forwarded downstream as X-User-Id / X-User-Role headers so
 * services don't need to re-parse the JWT for basic identity (they still
 * independently validate the signature themselves for defense in depth).
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/token/refresh",
            "/register",
            "/actuator/health"
    );

    private final JwtValidator jwtValidator;

    public JwtAuthGlobalFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        Optional<Claims> claims = jwtValidator.validate(authHeader.substring(7));
        if (claims.isEmpty()) {
            return unauthorized(exchange);
        }

        String userId = claims.get().getSubject();
        String role = claims.get().get("role", String.class);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
