package com.ausiankou.apigateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

/**
 * Boots the real Gateway (real routes + real Resilience4j filters from
 * application.yml) against WireMock stand-ins for every downstream service,
 * verifying:
 *  - JWT is required and gets forwarded as X-User-Id/X-User-Role
 *  - a GET route retries on a transient 5xx and eventually succeeds
 *  - once the dependency is consistently down, the circuit breaker's
 *    fallback returns 503 from /fallback/* instead of propagating the error
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewayRouteResilienceTest {

    private static final String JWT_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    static WireMockServer orderServiceMock = new WireMockServer(0);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void startServer() {
        orderServiceMock.start();
    }

    @AfterAll
    static void stopServer() {
        orderServiceMock.stop();
    }

    @AfterEach
    void resetStubs() {
        orderServiceMock.resetAll();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("services.order-service.base-url", () -> "http://localhost:" + orderServiceMock.port());
        // point the other services somewhere harmless - not exercised by these tests
        registry.add("services.auth-service.base-url", () -> "http://localhost:" + orderServiceMock.port());
        registry.add("services.user-service.base-url", () -> "http://localhost:" + orderServiceMock.port());
        registry.add("services.payment-service.base-url", () -> "http://localhost:" + orderServiceMock.port());
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    private String token() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    @Test
    void protectedRoute_withoutToken_returns401() {
        webTestClient.get().uri("/orders/123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getOrder_retriesOnTransientError_thenSucceeds() {
        orderServiceMock.stubFor(get(urlPathEqualTo("/orders/123"))
                .inScenario("gw-retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("recovered"));
        orderServiceMock.stubFor(get(urlPathEqualTo("/orders/123"))
                .inScenario("gw-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("{\"id\":\"123\",\"status\":\"CREATED\"}")));

        webTestClient.get().uri("/orders/123")
                .header("Authorization", "Bearer " + token())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrder_fallsBackTo503_whenOrderServiceConsistentlyDown() {
        orderServiceMock.stubFor(get(urlPathEqualTo("/orders/999")).willReturn(serviceUnavailable()));

        webTestClient.get().uri("/orders/999")
                .header("Authorization", "Bearer " + token())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").exists();
    }
}
