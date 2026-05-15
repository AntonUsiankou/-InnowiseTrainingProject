package com.ausiankou.apigateway.filter;


import com.ausiankou.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",

            "/actuator/health",
            "/actuator/info",
            "/fallback/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Проверяем публичные пути
        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            return chain.filter(exchange);
        }

        // Проверяем Authorization header
        List<String> authHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("Missing Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing Authorization header");
        }

        String authHeader = authHeaders.get(0);
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            return unauthorizedResponse(exchange, "Invalid Authorization header format");
        }

        String token = authHeader.substring(7);

        return jwtUtil.validateToken(token)
                .flatMap(claims -> {
                    Long userId = Long.parseLong(claims.getSubject());
                    Long issuedAt = claims.getIssuedAt().getTime();

                    // Проверяем, не были ли инвалидированы все токены пользователя
                    return jwtUtil.areUserTokensInvalidated(userId, issuedAt)
                            .flatMap(invalidated -> {
                                if (invalidated) {
                                    log.warn("All tokens for user {} have been invalidated", userId);
                                    return unauthorizedResponse(exchange, "Session expired - please login again");
                                }

                                // Добавляем user data в headers для downstream
                                String email = claims.get("email", String.class);
                                String roles = claims.get("roles", String.class);

                                log.debug("Validated token for user: {}, email: {}, path: {}", userId, email, path);

                                ServerHttpRequest mutatedRequest = request.mutate()
                                        .header("X-User-Id", String.valueOf(userId))
                                        .header("X-User-Email", email != null ? email : "")
                                        .header("X-User-Roles", roles != null ? roles : "")
                                        .build();

                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
                })
                .onErrorResume(e -> {
                    log.error("Token validation failed for path {}: {}", path, e.getMessage());
                    return unauthorizedResponse(exchange, e.getMessage());
                });
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> pathMatcher.match(publicPath, path));
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("WWW-Authenticate", "Bearer realm=\"API Gateway\"");
        log.warn("Unauthorized access: {}", message);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // Наивысший приоритет
    }
}