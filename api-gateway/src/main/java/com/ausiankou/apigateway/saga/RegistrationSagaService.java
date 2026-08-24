package com.ausiankou.apigateway.saga;

import com.ausiankou.apigateway.dto.*;
import com.ausiankou.apigateway.dto.*;
import com.ausiankou.apigateway.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Task: "Customize user registration flow: save user credentials into
 * Authentication Service and save user data into User Service (provide
 * rollback methods in case failed transaction in Authentication Service)".
 *
 * Implemented as an orchestrated saga:
 *   1) POST /auth/credentials   -> Auth Service persists login/password/role
 *   2) POST /users              -> User Service persists the profile
 *   3) If step 2 fails          -> compensate by DELETE /auth/credentials/{login}
 *
 * Both downstream calls go through their own circuit breaker + retry
 * (configured in application.yml) so a flaky dependency doesn't wedge the
 * whole registration flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationSagaService {

    private final WebClient.Builder webClientBuilder;

    @org.springframework.beans.factory.annotation.Value("${services.auth-service.base-url}")
    private String authServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${services.user-service.base-url}")
    private String userServiceUrl;

    public Mono<RegisterResponse> register(RegisterRequest request) {
        WebClient authClient = webClientBuilder.baseUrl(authServiceUrl).build();
        WebClient userClient = webClientBuilder.baseUrl(userServiceUrl).build();

        CredentialsRequest credentialsRequest =
                new CredentialsRequest(request.login(), request.password(), "USER", null);

        // Step 1: Auth Service
        return authClient.post()
                .uri("/auth/credentials")
                .bodyValue(credentialsRequest)
                .retrieve()
                .bodyToMono(CredentialsId.class) // Auth Service returns the created credential id
                .onErrorMap(ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Registration failed at Authentication Service: " + ex.getMessage(), ex))
                // Step 2: User Service
                .flatMap(credentials -> {
                    UserCreateRequest userCreateRequest = new UserCreateRequest(
                            request.name(), request.surname(), request.birthDate(), request.email());

                    return userClient.post()
                            .uri("/users")
                            .bodyValue(userCreateRequest)
                            .retrieve()
                            .bodyToMono(UserDto.class)
                            .map(user -> new RegisterResponse(user.id(), request.login(), user.email()))
                            // Step 3 (compensation): roll back credentials if User Service step fails
                            .onErrorResume(ex -> {
                                log.warn("User Service step failed during registration, rolling back credentials for login={}",
                                        request.login(), ex);
                                return rollbackCredentials(authClient, request.login())
                                        .then(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                                "Registration failed at User Service, credentials rolled back: " + ex.getMessage(), ex)));
                            });
                });
    }

    private Mono<Void> rollbackCredentials(WebClient authClient, String login) {
        return authClient.delete()
                .uri("/auth/credentials/{login}", login)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Rolled back credentials for login={}", login))
                .doOnError(e -> log.error("CRITICAL: failed to roll back credentials for login={}. Manual cleanup required.", login, e))
                .then();
    }
}
