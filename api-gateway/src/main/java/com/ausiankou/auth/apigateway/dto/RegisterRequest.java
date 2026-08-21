package com.ausiankou.auth.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank String login,
        @NotBlank String password,
        @NotBlank String name,
        @NotBlank String surname,
        LocalDate birthDate,
        @NotBlank @Email String email
) {}
