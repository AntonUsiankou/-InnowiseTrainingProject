package com.ausiankou.auth.security;

import com.ausiankou.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates JWT access/refresh tokens.
 * Token payload always carries userId + role so downstream services
 * (User/Order/Payment) can authorize without another network call.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-ms:900000}") long accessTokenTtlMs,       // 15 min
            @Value("${jwt.refresh-ttl-ms:604800000}") long refreshTokenTtlMs   // 7 days
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
    }

    public String generateAccessToken(UUID userId, Role role) {
        return buildToken(userId, role, accessTokenTtlMs, "access");
    }

    public String generateRefreshToken(UUID userId, Role role) {
        return buildToken(userId, role, refreshTokenTtlMs, "refresh");
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlMs / 1000;
    }

    private String buildToken(UUID userId, Role role, long ttlMs, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(signingKey)
                .compact();
    }

    /** Throws JwtException on invalid/expired token - caller (or global handler) maps it to 401. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get("role", String.class));
    }

    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
