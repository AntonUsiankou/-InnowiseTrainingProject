package com.ausiankou.auth.apigateway.saga;

import com.ausiankou.auth.apigateway.dto.RegisterRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the registration saga end to end against WireMock stubs for both
 * downstream services: happy path, and the compensating rollback when the
 * User Service step fails after credentials were already persisted.
 */
class RegistrationSagaServiceTest {

    static WireMockServer authServiceMock = new WireMockServer(0);
    static WireMockServer userServiceMock = new WireMockServer(0);

    private RegistrationSagaService sagaService;

    @BeforeAll
    static void startServers() {
        authServiceMock.start();
        userServiceMock.start();
    }

    @AfterAll
    static void stopServers() {
        authServiceMock.stop();
        userServiceMock.stop();
    }

    @AfterEach
    void resetStubs() {
        authServiceMock.resetAll();
        userServiceMock.resetAll();
    }

    private void initSaga() {
        sagaService = new RegistrationSagaService(WebClient.builder());
        ReflectionTestUtils.setField(sagaService, "authServiceUrl", "http://localhost:" + authServiceMock.port());
        ReflectionTestUtils.setField(sagaService, "userServiceUrl", "http://localhost:" + userServiceMock.port());
    }

    private RegisterRequest sampleRequest() {
        return new RegisterRequest("anton", "Passw0rd!", "Anton", "K", LocalDate.of(1995, 1, 1), "anton@example.com");
    }

    @Test
    void register_happyPath_callsBothServicesAndReturnsUser() {
        initSaga();
        authServiceMock.stubFor(post(urlEqualTo("/auth/credentials"))
                .willReturn(okJson("{\"id\":\"22222222-2222-2222-2222-222222222222\"}")));
        userServiceMock.stubFor(post(urlEqualTo("/users"))
                .willReturn(okJson("""
                        {"id":"11111111-1111-1111-1111-111111111111","name":"Anton","surname":"K","email":"anton@example.com"}
                        """)));

        StepVerifier.create(sagaService.register(sampleRequest()))
                .assertNext(response -> {
                    assertThat(response.login()).isEqualTo("anton");
                    assertThat(response.email()).isEqualTo("anton@example.com");
                })
                .verifyComplete();

        userServiceMock.verify(postRequestedFor(urlEqualTo("/users")));
    }

    @Test
    void register_userServiceFails_rollsBackCredentialsAndPropagatesError() {
        initSaga();
        authServiceMock.stubFor(post(urlEqualTo("/auth/credentials"))
                .willReturn(okJson("{\"id\":\"22222222-2222-2222-2222-222222222222\"}")));
        authServiceMock.stubFor(delete(urlEqualTo("/auth/credentials/anton"))
                .willReturn(noContent()));
        userServiceMock.stubFor(post(urlEqualTo("/users"))
                .willReturn(serverError()));

        StepVerifier.create(sagaService.register(sampleRequest()))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException
                        && ((ResponseStatusException) ex).getStatusCode().value() == 502)
                .verify();

        // the compensating action must have been called
        authServiceMock.verify(deleteRequestedFor(urlEqualTo("/auth/credentials/anton")));
    }

    @Test
    void register_authServiceFails_neverCallsUserService() {
        initSaga();
        authServiceMock.stubFor(post(urlEqualTo("/auth/credentials"))
                .willReturn(WireMock.aResponse().withStatus(409))); // login already taken

        StepVerifier.create(sagaService.register(sampleRequest()))
                .expectError(ResponseStatusException.class)
                .verify();

        userServiceMock.verify(0, postRequestedFor(urlEqualTo("/users")));
    }
}
