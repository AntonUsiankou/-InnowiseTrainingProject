package com.ausiankou.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateTokenRequest(@NotBlank String token) {}
