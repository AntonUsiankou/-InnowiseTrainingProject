package com.ausiankou.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh.expiration.604800000}")
    private long refreshExpirationMs;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final String USER_REFRESH_PREFIX = "refresh:user:";

    /**
     * Create new refresh token and store in Redis
     *
     * @param userId user ID from User Service
     * @param email user email
     * @param role user role
     * @return generated refresh token
     */
    public String createRefreshToken(Long userId, String email, String role){
        // Revoke old tokens
        revokeAllUserTokens(userId);

        String token = UUID.randomUUID().toString();
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String userKey = USER_REFRESH_PREFIX + userId;

        // Store token data
        String tokenData = userId + ":" + email + ":" + role;
        redisTemplate.opsForValue().set(tokenKey, tokenData, refreshExpirationMs, TimeUnit.MILLISECONDS);

        // Store mapping user -> token
        redisTemplate.opsForValue().set(userKey, token, refreshExpirationMs, TimeUnit.MILLISECONDS);

        log.debug("Refresh token created for user: {}", email);
        return token;
    }

    /**
     * Get refresh token data from Redis
     *
     * @param token refresh token
     * @return RefreshTokenData containing userId, email, role or null if not found
     */
    public RefreshTokenData getRefreshTokenData(String token){
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String tokenData = redisTemplate.opsForValue().get(tokenKey);

        if(tokenData == null) {
            log.warn("Refresh token not found: {}", token);
            return null;
        }

        String[] parts = tokenData.split(":");
        if(parts.length != 3){
            return null;
        }

        return RefreshTokenData.builder()
                .userId(Long.parseLong(parts[0]))
                .email(parts[1])
                .role(parts[2])
                .build();
    }

    /**
     * Revoke all refresh tokens for a specific user
     *
     * @param userId user ID
     */
    public void revokeAllUserTokens(Long userId) {
        String userKey = USER_REFRESH_PREFIX + userId;
        String oldToken = redisTemplate.opsForValue().get(userKey);

        if (oldToken != null) {
            String oldTokenKey = REFRESH_TOKEN_PREFIX + oldToken;
            redisTemplate.delete(oldTokenKey);
            log.debug("Revoked old refresh token for user: {}", userId);
        }

        redisTemplate.delete(userKey);
    }

    /**
     * Delete specific refresh token
     *
     * @param token refresh token to delete
     */
    public void deleteRefreshToken(String token){
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String tokenData = redisTemplate.opsForValue().get(tokenKey);

        if (tokenData != null) {
            String[] parts = tokenData.split(":");
            if (parts.length == 3) {
                Long userId = Long.parseLong(parts[0]);
                String userKey = USER_REFRESH_PREFIX + userId;
                redisTemplate.delete(userKey);
            }
        }

        redisTemplate.delete(tokenKey);
        log.debug("Refresh token deleted: {}", token);
    }

    @Builder
    @Data
    public static class RefreshTokenData{
        private Long userId;
        private String email;
        private String role;
    }
}
