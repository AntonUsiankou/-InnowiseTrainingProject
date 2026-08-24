package com.ausiankou.auth.service;

import com.ausiankou.auth.dto.*;
import com.ausiankou.auth.entity.Credential;
import com.ausiankou.auth.entity.Role;
import com.ausiankou.auth.exception.AuthException;
import com.ausiankou.auth.repository.CredentialRepository;
import com.ausiankou.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UUID saveCredentials(RegisterCredentialsRequest request) {
        if (credentialRepository.existsByLogin(request.login())) {
            throw AuthException.loginTaken();
        }
        Credential credential = Credential.builder()
                .login(request.login())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() == null ? Role.USER : request.role())
                .userId(request.userId())
                .build();
        return credentialRepository.save(credential).getId();
    }

    /** Compensating action for the registration saga if User Service creation fails downstream. */
    @Transactional
    public void deleteCredentialsByLogin(String login) {
        credentialRepository.findByLogin(login).ifPresent(credentialRepository::delete);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Credential credential = credentialRepository.findByLogin(request.login())
                .orElseThrow(AuthException::invalidCredentials);

        if (!credential.isEnabled()) {
            throw AuthException.userDisabled();
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw AuthException.invalidCredentials();
        }

        UUID subjectId = credential.getUserId() != null ? credential.getUserId() : credential.getId();
        String access = jwtService.generateAccessToken(subjectId, credential.getRole());
        String refresh = jwtService.generateRefreshToken(subjectId, credential.getRole());
        return new TokenResponse(access, refresh, jwtService.accessTokenTtlSeconds());
    }

    public ValidateTokenResponse validate(ValidateTokenRequest request) {
        try {
            Claims claims = jwtService.parseAndValidate(request.token());
            return new ValidateTokenResponse(true, jwtService.extractUserId(claims), jwtService.extractRole(claims).name());
        } catch (Exception e) {
            return new ValidateTokenResponse(false, null, null);
        }
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(request.refreshToken());
        } catch (Exception e) {
            throw AuthException.invalidToken();
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw AuthException.invalidToken();
        }
        UUID userId = jwtService.extractUserId(claims);
        Role role = jwtService.extractRole(claims);
        String access = jwtService.generateAccessToken(userId, role);
        String refresh = jwtService.generateRefreshToken(userId, role);
        return new TokenResponse(access, refresh, jwtService.accessTokenTtlSeconds());
    }
}
