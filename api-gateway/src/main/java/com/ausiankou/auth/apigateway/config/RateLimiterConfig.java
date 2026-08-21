package com.ausiankou.auth.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Backs the Redis-based RequestRateLimiter filter (see application.yml) which
 * protects downstream services from being overwhelmed when we have thousands
 * of concurrent clients. Keyed by client IP by default; swap for X-User-Id
 * once authenticated if you want per-user limits instead of per-IP.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .defaultIfEmpty("unknown");
    }
}
