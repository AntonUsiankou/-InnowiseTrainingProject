package com.ausiankou.auth.apigateway.controller;

import com.ausiankou.auth.apigateway.dto.RegisterRequest;
import com.ausiankou.auth.apigateway.dto.RegisterResponse;
import com.ausiankou.auth.apigateway.saga.RegistrationSagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Public entry point for the registration saga - excluded from JWT checks. */
@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationSagaService registrationSagaService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationSagaService.register(request);
    }
}
