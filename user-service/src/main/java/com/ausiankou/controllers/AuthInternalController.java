package com.ausiankou.controllers;

import com.ausiankou.dto.AuthUserDto;
import com.ausiankou.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthInternalController {

    private final UserService userService;

    @GetMapping("/user/{email}")
    public ResponseEntity<AuthUserDto> getUserByEmail(@PathVariable String email){
        log.info("Внутренний запрос юзера по email: {}", email);
        return ResponseEntity.ok(userService.getAuthUserByEmail(email));
    }

    @GetMapping("/user/id/{id}")
    public ResponseEntity<AuthUserDto> getUserById(@PathVariable Long id) {
        log.info("Internal request for user by id: {}", id);
        return ResponseEntity.ok(userService.getAuthUserById(id));
    }
}
