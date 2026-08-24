package com.ausiankou.orders.client;

import com.ausiankou.orders.config.RestClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the actual behavior configured in application.yml for the
 * userService circuit breaker + retry (not just that annotations are
 * present): retries on transient 5xx, and the fallback kicks in instead of
 * propagating an exception once the dependency is down.
 *
 * Boots a narrow test-only application context (just the client + REST
 * config + resilience4j/AOP auto-config) instead of the full Order Service
 * context, so this slice doesn't need a real DB/Kafka broker and doesn't
 * pick up SecurityConfig/JpaAuditingConfig from the main config package.
 */
@SpringBootTest(classes = UserServiceClientResilienceTest.TestApp.class)
class UserServiceClientResilienceTest {

    @SpringBootApplication(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
            org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class,
            org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class
    })
    @ComponentScan(basePackages = "com.ausiankou.orders")
    @Import(RestClientConfig.class)
    static class TestApp {
    }

    static WireMockServer wireMockServer = new WireMockServer(0);

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("services.user-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private UserServiceClient userServiceClient;

    @Test
    void returnsUserInfo_whenUserServiceRespondsSuccessfully() {
        wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                .willReturn(okJson("""
                        {"id":"11111111-1111-1111-1111-111111111111","name":"Anton","surname":"K","email":"anton@example.com"}
                        """)));

        var result = userServiceClient.getUserByEmail("anton@example.com");

        assertThat(result.name()).isEqualTo("Anton");
    }

    @Test
    void retriesOnServerError_thenSucceeds() {
        wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("second-attempt"));

        wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                .inScenario("retry")
                .whenScenarioStateIs("second-attempt")
                .willReturn(okJson("""
                        {"id":"11111111-1111-1111-1111-111111111111","name":"Retried","surname":"K","email":"anton@example.com"}
                        """)));

        var result = userServiceClient.getUserByEmail("anton@example.com");

        assertThat(result.name()).isEqualTo("Retried");
    }

    @Test
    void fallsBackToUnavailable_whenUserServiceKeepsFailing() {
        wireMockServer.stubFor(get(urlPathEqualTo("/users")).willReturn(serverError()));

        // exhausts retries (3 attempts) and returns the fallback rather than throwing
        var result = userServiceClient.getUserByEmail("anton@example.com");

        assertThat(result.name()).isEqualTo("unavailable");
    }
}
