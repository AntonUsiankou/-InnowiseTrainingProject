package com.ausiankou.service;

import com.ausiankou.client.UserServiceClient;
import com.ausiankou.dto.*;
import com.ausiankou.exception.CustomExceptions;
import com.ausiankou.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service interface for authentication operations
 * Handles user registration, login, token validation, refresh, and logout
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserServiceClient userServiceClient;
     private final JwtService jwtService;
     private final PasswordEncoder passwordEncoder;
     private final RefreshTokenService refreshTokenService;

    /**
     * Register a new user
     *
     * @param request registration request containing user details
     * @return authentication response with access and refresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.ConflictException if user already exists
     */
     @Transactional
     public AuthResponse register(RegistrationRequest request){
         log.info("Попытка регистрации по электронной почте: {}", request.getEmail());
         AuthUserDto existingUser = userServiceClient.getUserByEmail(request.getEmail());
         if(existingUser != null){
             log.warn("Пользователь уже существует: ", request.getEmail());
             throw new CustomExceptions.ConflictException("Пользователь  таким мылом уже существует: " + request.getEmail());
         }
         AuthUserDto createdUser = userServiceClient.createUser(request);

         if (createdUser == null || createdUser.getId() == null) {
             log.error("Ошибка создания пользователя в UserService");
             throw new RuntimeException("Ошибка создания пользователя");
         }
         log.info("Пользователь успешно создан в UserService: {}, id: {}", createdUser.getEmail(), createdUser.getId());
         String accessToken = jwtService.generateAccessToken(
                 createdUser.getEmail(),
                 createdUser.getId(),
                 createdUser.getRole()
         );
         String refreshToken = refreshTokenService.createRefreshToken(
                 createdUser.getId(),
                 createdUser.getEmail(),
                 createdUser.getRole()
         );
         log.info("Пользователь зарегистрирован: {}, role: {}", createdUser.getEmail(), createdUser.getRole());
         return AuthResponse.builder()
                 .accessToken(accessToken)
                 .refreshToken(refreshToken)
                 .tokenType("Bearer")
                 .expiresIn(900000l)
                 .email(createdUser.getEmail())
                 .role(createdUser.getRole())
                 .build();
     }

    /**
     * Authenticate user and generate tokens
     *
     * @param request login request with email and password
     * @return authentication response with access and refresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.UnauthorizedActionException if credentials are invalid
     */
     public AuthResponse login(LoginRequest request){
         log.info("Попытка входа в электронную почту: {}", request.getEmail());

         boolean isValid = userServiceClient.validateCredentials(request.getEmail(), request.getPassword());
         if (!isValid) {
             throw new CustomExceptions.UnauthorizedActionException("Invalid email or password");
         }
         AuthUserDto user = userServiceClient.getUserByEmail(request.getEmail());

         if (user == null) {
             log.warn("Пользователь не найден: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Неверный email или пароль");
         }

         if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
             log.warn("Инвалидный пассворд: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Неверный email или пароль");
         }

         if (!user.getActive()) {
             log.warn("Пользователь не активный: {}", request.getEmail());
             throw new CustomExceptions.UnauthorizedActionException("Пользователь деактивирован");
         }

         log.info("Пользователь аутифицирован: {}, роль: {}", user.getEmail(), user.getRole());

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

    /**
     * Validate JWT token for API Gateway
     *
     * @param token JWT token to validate
     * @return validation response with token status and user details if valid
     */
     public ValidateTokenResponse validateToken(String token){
         log.debug("Валидация токена");
         try {
             if (!jwtService.isTokenValid(token)) {
                 log.warn("Токен не валиден");
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("Неверный формат токена или подпись.")
                         .build();
             }

             if (jwtService.isTokenExpired(token)) {
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("Срок действия токена истек.")
                         .build();
             }

             String email = jwtService.extractUsername(token);
             Long userId = jwtService.extractUserId(token);
             String role = jwtService.extractRole(token);

             AuthUserDto user = userServiceClient.getUserById(userId);

             if (user == null) {
                 log.warn("Пользователь не найден для получения токена: {}", userId);
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("Пользователь не найден")
                         .build();
             }

             if (!user.getActive()) {
                 log.warn("Пользователь деактивирован: {}", email);
                 return ValidateTokenResponse.builder()
                         .valid(false)
                         .message("Пользователь деактивирован")
                         .build();
             }

             log.info("Токен успешно подтвержден для пользователя: {}, роль: {}", email, role);

             return ValidateTokenResponse.builder()
                     .valid(true)
                     .userId(userId)
                     .email(email)
                     .role(role)
                     .message("Токен валиден")
                     .build();

         } catch (Exception e) {
             log.error("Ошибка проверки токена: {}", e.getMessage());
             return ValidateTokenResponse.builder()
                     .valid(false)
                     .message("Проверка токена не удалась: " + e.getMessage())
                     .build();
         }
     }
    /**
     * Refresh access token using refresh token
     *
     * @param request refresh token request
     * @return new authentication response with fresh tokens
     * @throws com.ausiankou.exception.CustomExceptions.UnauthorizedActionException if refresh token is invalid
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Попытка обновить рефпешь токен");

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

        log.info("Токен обновлен для пользователя: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    /**
     * Logout user by invalidating refresh token
     *
     * @param refreshToken refresh token to invalidate
     */
    public void logout(String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
        log.info("Пользоваетль вышел");
    }

}
