package com.ausiankou.service;

import com.ausiankou.client.UserServiceClient;
import com.ausiankou.dto.*;
import com.ausiankou.exception.CustomExceptions;
import com.ausiankou.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserServiceClient userServiceClient;
     private final JwtService jwtService;
     private final PasswordEncoder passwordEncoder;
     private final RefreshTokenService refreshTokenService;

     public AuthResponse login(LoginRequest request){
         log.info("Login attempt for email: {}", request.getEmail());

         AuthUserDto user = userServiceClient.getUserByEmail(request.getEmail());

         if (user == null) {
             log.warn("User not found: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Неверный email или пароль");
         }

         if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
             log.warn("Invalid password for user: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Неверный email или пароль");
         }

         if(!user.getActive()){
             log.warn("User is deactivated: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Пользователь деактивирован");
         }
         log.info("User authenticated successfully: {}, role: {}", user.getEmail(), user.getRole());

         String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getRole());

         String refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getEmail(), user.getRole());

         return AuthResponse.builder()
                 .accessToken(accessToken)
                 .refreshToken(refreshToken)
                 .tokenType("Bearer")
                 .expiresIn(900000L)
                 .email(user.getEmail())
                 .role(user.getRole())
                 .build();
     }

     public ValidateTokenResponse validateToken(String token){
         log.debug("Validate token");
         try{
             if(!jwtService.isTokenValid(token)){
                 log.warn("Token is invalid");
                return ValidateTokenResponse.builder()
                        .valid(false)
                        .message("Invalid token format or signature")
                        .build();
             }
             if (jwtService.isTokenExpired(token)) {
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("Token has expired")
                         .build();
             }

             String email = jwtService.extractUsername(token);
             Long userId = jwtService.extractUserId(token);
             String role = jwtService.extractRole(token);

             AuthUserDto user = userServiceClient.getUserById(userId);

             if(user == null){
                 log.warn("User not found for token: {}", userId);
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("User not found")
                         .build();
             }
             if (!user.getActive()) {
                 log.warn("User is deactivated: {}", email);
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("User is deactivated")
                         .build();
             }

             log.info("Token validated successfully for user: {}, role: {}", email, role);

             return ValidateTokenResponse.builder()
                     .valid(true)
                     .userId(userId)
                     .email(email)
                     .role(role)
                     .message("Token is valid")
                     .build();

         } catch (Exception e) {
             log.error("Token validation error: {}", e.getMessage());
             return ValidateTokenResponse.builder()
                     .valid(false)
                     .message("Token validation failed: " + e.getMessage())
                     .build();
         }
     }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token attempt");

        RefreshTokenData tokenData = refreshTokenService.getRefreshTokenData(request.getRefreshToken());

        if (tokenData == null) {
            throw new CustomExceptions.UnauthorizedActionException("Недействительный refresh token");
        }

        AuthUserDto user = userServiceClient.getUserById(tokenData.getUserId());

        if (user == null) {
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());
            throw new CustomExceptions.UnauthorizedActionException("Пользователь не найден");
        }

        if (!user.getActive()) {
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());
            throw new CustomExceptions.UnauthorizedActionException("Пользователь деактивирован");
        }

        refreshTokenService.deleteRefreshToken(request.getRefreshToken());

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getRole());
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getEmail(), user.getRole());

        log.info("Tokens refreshed for user: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
     public void logout(String refreshToken){
         refreshTokenService.deleteRefreshToken(refreshToken);
         log.info("User logged out");
     }

}
