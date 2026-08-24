package com.ausiankou.orders.client;

import com.ausiankou.orders.dto.UserInfoDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Synchronous REST call to User Service to enrich order responses (Task 3:
 * "Add communication between Order Service and User Service to get user info
 * by email" + "Add circuit breaker pattern for this communication").
 *
 * Retry wraps the circuit breaker: transient failures get a couple of quick
 * retries; once the failure rate trips the breaker, calls fail fast via the
 * fallback below instead of piling up threads waiting on a dead dependency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUserByEmail")
    @Retry(name = "userService")
    public UserInfoDto getUserByEmail(String email) {
        return userServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users").queryParam("email", email).build())
                .retrieve()
                .body(UserInfoDto.class);
    }

    /** Fallback keeps order flows alive when User Service is down/degraded. */
    @SuppressWarnings("unused")
    private UserInfoDto fallbackGetUserByEmail(String email, Throwable throwable) {
        log.warn("User Service call failed for email={}, falling back. Reason: {}", email, throwable.toString());
        return UserInfoDto.unavailable();
    }
}
