package com.ausiankou.apigateway.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public JwtUtil(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Валидация JWT токена
     */
    public Mono<Claims> validateToken(String token) {
        return isTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Token is blacklisted");
                        return Mono.error(new RuntimeException("Token is blacklisted - please login again"));
                    }

                    try {
                        Claims claims = Jwts.parserBuilder()
                                .setSigningKey(getSigningKey())
                                .setAllowedClockSkewSeconds(60)
                                .build()
                                .parseClaimsJws(token)
                                .getBody();

                        if (claims.getExpiration().before(new Date())) {
                            log.warn("Token expired");
                            return Mono.error(new RuntimeException("Token expired"));
                        }

                        log.debug("Token validated for user: {}", claims.getSubject());
                        return Mono.just(claims);

                    } catch (ExpiredJwtException e) {
                        log.warn("JWT expired: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Token expired"));
                    } catch (UnsupportedJwtException e) {
                        log.warn("Unsupported JWT: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Unsupported token"));
                    } catch (MalformedJwtException e) {
                        log.warn("Malformed JWT: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Malformed token"));
                    } catch (SignatureException e) {
                        log.warn("Invalid signature: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Invalid token signature"));
                    } catch (IllegalArgumentException e) {
                        log.warn("Illegal argument: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Token is invalid"));
                    }
                });
    }


    private Mono<Boolean> isTokenBlacklisted(String token) {
        String key = getBlacklistKey(token);
        return redisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.debug("Token found in blacklist: {}", token.substring(0, Math.min(20, token.length())) + "...");
                    }
                });
    }


    public Mono<Void> blacklistToken(String token) {
        return extractExpiration(token)
                .flatMap(expirationDate -> {
                    long ttlMillis = expirationDate.getTime() - System.currentTimeMillis();
                    if (ttlMillis <= 0) {
                        log.warn("Token already expired, no need to blacklist");
                        return Mono.empty();
                    }

                    String key = getBlacklistKey(token);
                    Duration ttl = Duration.ofMillis(ttlMillis);

                    log.info("Blacklisting token with TTL: {} seconds", ttl.getSeconds());

                    return redisTemplate.opsForValue()
                            .set(key, "true", ttl)
                            .doOnSuccess(v -> log.info("Token successfully blacklisted, TTL: {} ms", ttlMillis))
                            .doOnError(e -> log.error("Failed to blacklist token: {}", e.getMessage()))
                            .then();
                });
    }

    public Mono<Void> blacklistAllUserTokens(Long userId) {
        String pattern = getBlacklistKeyPattern(userId);
        log.info("Blacklisting all tokens for user: {}", userId);

        String key = "user:" + userId + ":tokens_invalidated";
        return redisTemplate.opsForValue()
                .set(key, String.valueOf(System.currentTimeMillis()), Duration.ofDays(7))
                .then();
    }

    public Mono<Boolean> areUserTokensInvalidated(Long userId, long tokenIssuedAt) {
        String key = "user:" + userId + ":tokens_invalidated";
        return redisTemplate.opsForValue()
                .get(key)
                .map(invalidatedAt -> Long.parseLong(invalidatedAt) > tokenIssuedAt)
                .defaultIfEmpty(false);
    }


    private Mono<Date> extractExpiration(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Mono.just(claims.getExpiration());
        } catch (Exception e) {
            log.error("Failed to extract expiration: {}", e.getMessage());
            return Mono.error(new RuntimeException("Invalid token"));
        }
    }

    public Mono<Long> extractUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Mono.just(Long.parseLong(claims.getSubject()));
        } catch (Exception e) {
            log.error("Failed to extract user ID: {}", e.getMessage());
            return Mono.error(new RuntimeException("Invalid token"));
        }
    }


    public Mono<Long> extractIssuedAt(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Mono.just(claims.getIssuedAt().getTime());
        } catch (Exception e) {
            log.error("Failed to extract issued at: {}", e.getMessage());
            return Mono.error(new RuntimeException("Invalid token"));
        }
    }

    private String getBlacklistKey(String token) {
        return "blacklist:token:" + token;
    }

    private String getBlacklistKeyPattern(Long userId) {
        return "blacklist:user:" + userId + ":*";
    }
}
