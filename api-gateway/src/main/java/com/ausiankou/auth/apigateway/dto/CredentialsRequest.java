package com.ausiankou.auth.apigateway.dto;

public record CredentialsRequest(String login, String password, String role, java.util.UUID userId) {}
