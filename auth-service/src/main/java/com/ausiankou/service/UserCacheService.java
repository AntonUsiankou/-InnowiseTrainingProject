package com.ausiankou.service;

import com.ausiankou.client.UserServiceClient;
import com.ausiankou.dto.AuthUserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    private final UserServiceClient userServiceClient;
    private final TokenBlacklistService tokenBlacklistService;

    private static final long USER_STATUS_CACHE_TTL_SECONDS = 300;
    private static final long USER_DATA_CACHE_TTL_SECONDS = 600;

    /**
     * Get user active status with caching
     * First check Redis cache, if not found - call UserService and cache result
     * @param userId User ID
     * @return true if user is active, false otherwise
     */
    public boolean isUserActive(Long userId) {
        Boolean cachedStatus = tokenBlacklistService.getUserStatusFromCache(userId);

        if (cachedStatus != null) {
            log.debug("User status retrieved from cache: userId={}, active={}", userId, cachedStatus);
            return cachedStatus;
        }

        try {
            log.debug("Cache miss for user status: userId={}, calling UserService", userId);
            AuthUserDto user = userServiceClient.getUserById(userId);

            if (user == null) {
                log.warn("User not found in UserService: userId={}", userId);
                return false;
            }

            boolean isActive = user.getActive();
            tokenBlacklistService.cacheUserStatus(userId, isActive, USER_STATUS_CACHE_TTL_SECONDS);

            return isActive;
        } catch (Exception e) {
            log.error("Error checking user active status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get full user data with caching
     * @param userId User ID
     * @return AuthUserDto or null if not found
     */
    public AuthUserDto getUserWithCache(Long userId) {
        return userServiceClient.getUserById(userId);
    }

    /**
     * Refresh user status cache when user status changes
     * @param userId User ID
     * @param active New active status
     */
    public void refreshUserStatus(Long userId, boolean active) {
        tokenBlacklistService.cacheUserStatus(userId, active, USER_STATUS_CACHE_TTL_SECONDS);
        log.info("User status cache refreshed: userId={}, active={}", userId, active);
    }
}
