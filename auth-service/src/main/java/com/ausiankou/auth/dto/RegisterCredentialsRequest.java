package com.ausiankou.auth.dto;

import com.ausiankou.auth.entity.Role;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Called internally by API Gateway during the registration saga. */
public record RegisterCredentialsRequest(
        @NotBlank String login,
        @NotBlank String password,
        Role role,
        UUID userId
) {}
