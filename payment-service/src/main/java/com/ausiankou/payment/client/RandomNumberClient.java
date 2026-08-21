package com.ausiankou.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;

/**
 * Calls an external "random number" API (e.g. random.org) to decide payment
 * outcome: even -> SUCCESS, odd -> FAILED (Task 3). Wrapped with retry +
 * circuit breaker since it's a call to a 3rd party outside our control; if it
 * keeps failing we fall back to a local SecureRandom generator rather than
 * blocking payment processing entirely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RandomNumberClient {

    private final RestClient externalApiRestClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @CircuitBreaker(name = "externalRandomApi", fallbackMethod = "fallbackRandomNumber")
    @Retry(name = "externalRandomApi")
    public int getRandomNumber() {
        Integer result = externalApiRestClient.get()
                .uri("/integers?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new")
                .retrieve()
                .body(Integer.class);
        if (result == null) {
            throw new IllegalStateException("External random API returned no body");
        }
        return result;
    }

    @SuppressWarnings("unused")
    private int fallbackRandomNumber(Throwable throwable) {
        log.warn("External random API unavailable, falling back to local RNG. Reason: {}", throwable.toString());
        return secureRandom.nextInt(100) + 1;
    }
}
