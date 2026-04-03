package com.ausiankou.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:token:";
    private static final String USER_STATUS_PREFIX = "user:status:";

    /**
     * Add token to blacklist
     * @param token JWT token to blacklist
     * @param expirationSeconds TTL in seconds
     */
    public void blacklistToken(String token, long expirationSeconds) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "revoked", expirationSeconds, TimeUnit.SECONDS);
        log.debug("Token blacklisted: {}", token);
    }

    /**
     * Check if token is blacklisted
     * @param token JWT token to check
     * @return true if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        Boolean isBlacklisted = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(isBlacklisted);
    }

    /**
     * Cache user status from User Service to avoid HTTP calls
     * @param userId User ID
     * @param active User active status
     * @param expirationSeconds Cache TTL
     */
    public void cacheUserStatus(Long userId, Boolean active, long expirationSeconds) {
        String key = USER_STATUS_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(active), expirationSeconds, TimeUnit.SECONDS);
        log.debug("User status cached: userId={}, active={}", userId, active);
    }

    /**
     * Get user status from cache
     * @param userId User ID
     * @return true if user is active, false if inactive or not found in cache
     */
    public Boolean getUserStatusFromCache(Long userId) {
        String key = USER_STATUS_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        return null;
    }

    /**
     * Invalidate user status cache when user status changes
     * @param userId User ID
     */
    public void invalidateUserStatusCache(Long userId) {
        String key = USER_STATUS_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("User status cache invalidated: userId={}", userId);
    }
}

