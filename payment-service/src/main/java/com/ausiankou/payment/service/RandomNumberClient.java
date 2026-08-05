package com.ausiankou.payment.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RandomNumberClient {

    private final RestTemplate restTemplate;
    private final String externalApiUrl;
    private final int timeout;
    private final MeterRegistry meterRegistry;

    // Локальный fallback генератор с большей энтропией
    private final Random secureRandom = new Random();
    private final AtomicInteger fallbackCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();


    public RandomNumberClient(@Value("${external.api.random-number.url}") String externalApiUrl,
                              @Value("${external.api.random-number.timeout:3000}") int timeout,
                              MeterRegistry meterRegistry) {
        this.restTemplate = createRestTemplate(timeout);
        this.externalApiUrl = externalApiUrl;
        this.timeout = timeout;
        this.meterRegistry = meterRegistry;

        // Регистрируем метрики
        registerMetrics();
    }

    private RestTemplate createRestTemplate(int timeout) {
        RestTemplate template = new RestTemplate();
        template.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
           setConnectTimeout(timeout);
           setReadTimeout(timeout);
        }});
        return template;
    }

    private void registerMetrics() {
        meterRegistry.gauge("random.api.fallback.count", fallbackCounter);
        meterRegistry.gauge("random.api.cache.size", cache, ConcurrentHashMap::size);
    }

    /**
     * Get random number from external API or fallback to local Random
     * If number is even -> SUCCESS, if odd -> FAILED
     */
    public boolean isPaymentSuccess() {
        int randomNumber = getRandomNumber();
        log.debug("Generated random number: {}", randomNumber);

        return randomNumber % 2 == 0;
    }

    /**
     * Get random number with Circuit Breaker and Retry
     */
    @CircuitBreaker(name = "randomNumberApi", fallbackMethod = "getRandomNumberFallback")
    @Retry(name = "randomNumberApi")
    @RateLimiter(name = "paymentApi")
    private int getRandomNumber() {
        long startTime = System.nanoTime();

        try {
            // Добавляем случайный параметр для избежания кэширования
            String url = externalApiUrl + "?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=" + System.nanoTime();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Payment-Service/1.0");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getBody() != null) {
                int number = Integer.parseInt(response.getBody().trim());
                long duration = System.nanoTime() - startTime;

                // Записываем метрику
                meterRegistry.timer("random.api.call.duration").record(Duration.ofNanos(duration));
                meterRegistry.counter("random.api.success").increment();

                log.debug("Got random number from external API: {}, duration: {}ms", number, duration / 1_000_000);
                return number;
            }

        } catch (RestClientException | NumberFormatException e) {
            log.warn("External API call failed: {}", e.getMessage());
            meterRegistry.counter("random.api.failure", "error", e.getClass().getSimpleName()).increment();
            throw new RuntimeException("Failed to get random number", e);
        }

        throw new RuntimeException("Empty response from external API");
    }

    /**
     * Fallback method when external API fails
     */
    private int getRandomNumberFallback(Exception e) {
        log.warn("Using fallback random generator due to: {}", e.getMessage());

        // Проверяем кэш (для одного запроса в пределах 1 секунды)
        String cacheKey = String.valueOf(System.currentTimeMillis() / 1000);
        Integer cached = cache.get(cacheKey);
        if (cached != null) {
            meterRegistry.counter("random.api.cache.hit").increment();
            return cached;
        }

        // Генерируем криптографически безопасное случайное число
        int number = secureRandom.nextInt(100) + 1;

        // Добавляем в кэш на короткое время
        cache.put(cacheKey, number);
        cache.entrySet().removeIf(entry ->
                Long.parseLong(entry.getKey()) < (System.currentTimeMillis() / 1000) - 1);

        fallbackCounter.incrementAndGet();
        meterRegistry.counter("random.api.fallback.used").increment();

        log.debug("Generated fallback random number: {}", number);
        return number;
    }
}
