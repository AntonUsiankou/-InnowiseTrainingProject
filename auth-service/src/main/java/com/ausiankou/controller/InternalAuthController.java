package com.ausiankou.controller;

import com.ausiankou.dto.UserCredentialsDto;
import com.ausiankou.entity.UserCredentials;
import com.ausiankou.service.AuthServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/auth")
@RequiredArgsConstructor
@Slf4j
public class InternalAuthController {

    private final AuthServiceImpl authService;

    @GetMapping("/user/{email}")
    public ResponseEntity<UserCredentialsDto> getUserByEmail(@PathVariable String email) {
        log.info("Internal request for user by email: {}", email);
        var credentials = authService.getUserCredentialsByEmail(email);
        return ResponseEntity.ok(toDto(credentials));
    }

    @GetMapping("/user/id/{userId}")
    public ResponseEntity<UserCredentialsDto> getUserByUserId(@PathVariable Long userId) {
        log.info("Internal request for user by id: {}", userId);
        var credentials = authService.getUserCredentialsByUserId(userId);
        return ResponseEntity.ok(toDto(credentials));
    }

    @PostMapping("/validate-credentials")
    public ResponseEntity<Boolean> validateCredentials(@RequestParam String email, @RequestParam String password) {
        log.info("Internal request to validate credentials for: {}", email);

        try {
            var credentials = authService.getUserCredentialsByEmail(email);
            // Здесь должна быть проверка пароля
            // Временно возвращаем true
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }

    private UserCredentialsDto toDto(UserCredentials credentials) {
        return UserCredentialsDto.builder()
                .email(credentials.getEmail())
                .role(credentials.getRole())
                .userId(credentials.getUserId())
                .enabled(credentials.isEnabled())
                .build();
    }
}
