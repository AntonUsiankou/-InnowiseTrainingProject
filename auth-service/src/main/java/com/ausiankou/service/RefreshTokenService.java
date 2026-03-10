package com.ausiankou.service;

import com.ausiankou.dto.RefreshTokenData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh.expiration:604800000}")
    private long refreshExpirationMs;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    public String createRefreshToken(Long userId, String email, String role){
        String token = UUID.randomUUID().toString();
        String key = REFRESH_TOKEN_PREFIX + token;
        String value = userId + ":" + email + ":" + role;
        redisTemplate.opsForValue().set(
                key,
                value,
                Duration.ofMillis(refreshExpirationMs)
        );
        log.debug("Refresh token created for user: {}", email);
        return token;
    }

    public RefreshTokenData getRefreshTokenData(String token){
        String key = REFRESH_TOKEN_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if(value == null){
            log.warn("Refresh token not found: {}", token);
            return null;
        }

        String[] parts = value.split(":");
        if(parts.length < 3){
            return null;
        }

        return RefreshTokenData.builder()
                .userId(Long.parseLong(parts[0]))
                .email(parts[1])
                .role(parts[2])
                .build();
    }

    public void deleteRefreshToken(String token){
        String key = REFRESH_TOKEN_PREFIX + token;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Refresh token deleted: {}", token);
        }
    }

    public void deleteAllUserRefreshTokens(Long userId){

    }
}
