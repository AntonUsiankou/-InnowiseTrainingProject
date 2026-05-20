package com.ausiankou.service;


import com.ausiankou.dto.AuthResponse;
import com.ausiankou.dto.LoginRequest;
import com.ausiankou.dto.RegistrationRequest;
import com.ausiankou.dto.ValidateTokenResponse;
import com.ausiankou.entity.UserCredentials;
import com.ausiankou.exception.CustomExceptions;
import com.ausiankou.repository.UserCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements IAuthService {

    private final UserCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // NOTE: This should call User Service to create user profile
    // через REST call, но НЕ хранить пароль там!
    // Для простоты пока возвращаем userId
    private Long createUserInUserService(RegistrationRequest request) {
        // Здесь должен быть REST call к user-service
        // /api/internal/users
        // Временно возвращаем фиктивный ID
        log.info("Should call User Service to create profile for: {}", request.getEmail());
        return System.currentTimeMillis(); // Temporary
    }

    @Override
    @Transactional
    public AuthResponse register(RegistrationRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        if (credentialsRepository.existsByEmail(request.getEmail())) {
            log.warn("User already exists: {}", request.getEmail());
            throw new CustomExceptions.ConflictException("User already exists with email: " + request.getEmail());
        }

        Long userId = createUserInUserService(request);

        UserCredentials credentials = UserCredentials.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .userId(userId)
                .enabled(true)
                .build();

        credentialsRepository.save(credentials);
        log.info("User registered successfully: {}", request.getEmail());

        String accessToken = jwtService.generateAccessToken(request.getEmail(), userId, request.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(userId, request.getEmail(), request.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .email(request.getEmail())
                .role(request.getRole())
                .userId(userId)
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        UserCredentials credentials = credentialsRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomExceptions.UnauthorizedActionException("Invalid email or password"));

        if(!passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash())){
            log.warn("Invalid password for email: {}", request.getEmail());
            throw new CustomExceptions.UnauthorizedActionException("Invalid email or password");
        }

        if(!credentials.isEnabled()) {
            log.warn("User is disabled: {}", request.getEmail());
            throw new CustomExceptions.UnauthorizedActionException("User account is disabled");
        }

        log.info("User logged in successfully: {}", request.getEmail());

        String accessToken = jwtService.generateAccessToken(
                credentials.getEmail(),
                credentials.getUserId(),
                credentials.getRole()
        );
        String refreshToken = refreshTokenService.createRefreshToken(
                credentials.getUserId(),
                credentials.getEmail(),
                credentials.getRole()
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .email(credentials.getEmail())
                .role(credentials.getRole())
                .userId(credentials.getUserId())
                .build();
    }

    @Override
    public ValidateTokenResponse validateToken(String token) {
        log.debug("Validating token");

        if(!jwtService.isTokenValid(token)) {
            log.warn("Invalid token");
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .message("Invalid token")
                    .build();
        }

        if(jwtService.isTokenExpired(token)) {
            log.warn("Token expired");
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .message("Token expired")
                    .build();
        }

        String email = jwtService.extractEmail(token);
        Long userId = jwtService.extractUserId(token);
        String role = jwtService.extractRole(token);

        UserCredentials credentials = credentialsRepository.findByEmail(email).orElse(null);
        if(credentials == null || ! credentials.isEnabled()) {
            log.warn("User not found or disabled: {}", email);
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .message("User not found or disabled")
                    .build();
        }

        log.debug("Token validated for user: {}, role: {}", email, role);

        return ValidateTokenResponse.builder()
                .valid(true)
                .message("Token is valid")
                .userId(userId)
                .email(email)
                .role(role)
                .build();
    }

    /**
     * Refresh access token using refresh token
     *
     * Steps:
     * 1. Get refresh token data from Redis
     * 2. Validate user still exists and is enabled
     * 3. Delete old refresh token
     * 4. Generate new access and refresh tokens
     *
     * @param refreshToken the refresh token from request
     * @return new authentication response with fresh tokens
     */
    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refresh token attempt");

        RefreshTokenService.RefreshTokenData tokenData = refreshTokenService.getRefreshTokenData(refreshToken);

        if( tokenData == null) {
            throw new CustomExceptions.UnauthorizedActionException("Invalid refresh token");
        }

        UserCredentials credentials = credentialsRepository.findUserId(tokenData.getUserId())
                .orElseThrow(() -> new CustomExceptions.UnauthorizedActionException("User not found"));
        if(!credentials.isEnabled()){
            refreshTokenService.deleteRefreshToken(refreshToken);
            throw new CustomExceptions.UnauthorizedActionException("User account is disabled");
        }

        refreshTokenService.deleteRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(
                credentials.getEmail(),
                credentials.getUserId(),
                credentials.getRole()
        );
        String newRefreshToken = refreshTokenService.createRefreshToken(
                credentials.getUserId(),
                credentials.getEmail(),
                credentials.getRole()
        );

        log.info("Tokens refreshed for user: {}", credentials.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .email(credentials.getEmail())
                .role(credentials.getRole())
                .userId(credentials.getUserId())
                .build();
    }

    /**
     * Logout user by invalidating refresh token
     *
     * @param refreshToken the refresh token to invalidate
     */
    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
        log.info("User logged out");
    }

    public UserCredentials getUserCredentialsByEmail(String email) {
        return credentialsRepository.findByEmail(email)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("User", "email", email));
    }

    public UserCredentials getUserCredentialsByUserId(Long userId) {
        return credentialsRepository.findUserId(userId)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("User", "userId", userId));
    }
}
