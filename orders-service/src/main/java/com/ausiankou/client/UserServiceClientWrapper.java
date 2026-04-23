package com.ausiankou.client;

import com.ausiankou.dto.UserInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientWrapper {
    private final UserServiceClient userServiceClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
    public UserInfo getUserById(Long userId) {
        log.info("Fetching user info for ID: {}", userId);
        return userServiceClient.getUserById(userId);
    }

    private UserInfo getUserFallback(Long userId, Throwable throwable) {
        log.error("Circuit breaker triggered for user ID: {}. Error: {}", userId, throwable.getMessage());

        return UserInfo.builder()
                .id(userId)
                .email("unknown@example.com")
                .firstName("Unknown")
                .lastName("User")
                .build();
    }
}
