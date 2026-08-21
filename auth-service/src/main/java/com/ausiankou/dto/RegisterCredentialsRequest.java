package com.ausiankou.dto;

import com.ausiankou.entity.Role;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Called internally by API Gateway during the registration saga. */
public record RegisterCredentialsRequest(
        @NotBlank String login,
        @NotBlank String password,
        Role role,
        UUID userId
) {}
