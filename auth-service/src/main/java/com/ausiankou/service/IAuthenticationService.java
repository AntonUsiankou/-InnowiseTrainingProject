package com.ausiankou.service;

import com.ausiankou.dto.*;

/**
 * Service interface for authentication operations
 * Handles user registration, login, token validation, refresh, and logout
 */
public interface IAuthenticationService {

    /**
     * Register a new user
     *
     * @param request registration request containing user details
     * @return authentication response with access and refresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.ConflictException if user already exists
     */
    AuthResponse register(RegistrationRequest request);

    /**
     * Authenticate user and generate tokens
     *
     * @param request login request with email and password
     * @return authentication response with access and refresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.UnauthorizedActionException if credentials are invalid
     */
    AuthResponse login(LoginRequest request);

    /**
     * Validate JWT token for API Gateway
     * Uses local validation with Redis cache - NO HTTP calls to User Service
     *
     * @param token JWT token to validate
     * @return validation response with token status and user details if valid
     */
    ValidateTokenResponse validateToken(String token);

    /**
     * Refresh access token using refresh token
     *
     * @param request refresh token request
     * @return new authentication response with fresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.UnauthorizedActionException if refresh token is invalid
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Logout user by invalidating refresh token
     *
     * @param refreshToken refresh token to invalidate
     */
    void logout(String refreshToken);
}