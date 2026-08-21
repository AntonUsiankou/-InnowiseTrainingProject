package com.ausiankou.dto;

import java.util.UUID;

public record ValidateTokenResponse(boolean valid, UUID userId, String role) {}
