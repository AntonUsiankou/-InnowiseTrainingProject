package com.ausiankou.auth.apigateway.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String login, String email) {}
