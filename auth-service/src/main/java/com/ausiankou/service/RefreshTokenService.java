package com.ausiankou.service;

import com.ausiankou.dto.RefreshTokenData;
import com.ausiankou.entity.RefreshToken;
import com.ausiankou.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh.expiration:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public String createRefreshToken(Long userId, String email, String role){
        refreshTokenRepository.revokeAllUserTokens(userId);
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .userId(userId)
                .userEmail(email)
                .userRole(role)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token created for user: {}", email);
        return token;
    }

    @Transactional
    public RefreshTokenData getRefreshTokenData(String token){
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token).orElse(null);
        if(refreshToken == null){
            log.warn("Refresh token not found: {}", token);
            return null;
        }
         if(refreshToken.isRevoked()){
             log.warn("Refresh token is revoked: {}", token);
             return null;
         }
         if(refreshToken.getExpiryDate().isBefore(Instant.now())){
             log.warn("Refresh token expired: {}", token);
             refreshTokenRepository.deleteByToken(token);
             return null;
         }
         return RefreshTokenData.builder()
                 .userId(refreshToken.getUserId())
                 .email(refreshToken.getUserEmail())
                 .role(refreshToken.getUserRole())
                 .build();
    }

    @Transactional
    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteByToken(token);
        log.debug("Refresh token deleted: {}", token);
    }
}
