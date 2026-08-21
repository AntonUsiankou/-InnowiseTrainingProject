package com.ausiankou.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {}
