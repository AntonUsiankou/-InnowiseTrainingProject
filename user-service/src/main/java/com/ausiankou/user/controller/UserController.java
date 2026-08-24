package com.ausiankou.user.controller;

import com.ausiankou.user.dto.*;
import com.ausiankou.user.service.UserServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserServiceImpl userServiceImpl;

    /** Called by API Gateway during the registration saga. */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userServiceImpl.createUser(request));
    }

    /** Admin can view any user; a regular user can only view themselves. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(userServiceImpl.getUser(id));
    }

    /** Internal lookup used by Order Service to enrich order responses. */
    @GetMapping(params = "email")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userServiceImpl.getUserByEmail(email));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserDto>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String surname,
            Pageable pageable) {
        return ResponseEntity.ok(userServiceImpl.searchUsers(name, surname, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserDto> updateUser(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userServiceImpl.updateUser(id, request));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        userServiceImpl.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userServiceImpl.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userServiceImpl.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /** Compensating (rollback) endpoint used by gateway saga if a later registration step fails. */
    @DeleteMapping("/rollback/{id}")
    public ResponseEntity<Void> rollback(@PathVariable UUID id) {
        userServiceImpl.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Cards ----

    @PostMapping("/{userId}/cards")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
    public ResponseEntity<PaymentCardDto> addCard(@PathVariable UUID userId, @Valid @RequestBody PaymentCardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userServiceImpl.addCard(userId, request));
    }

    @GetMapping("/{userId}/cards")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
    public ResponseEntity<java.util.List<PaymentCardDto>> getCards(@PathVariable UUID userId) {
        return ResponseEntity.ok(userServiceImpl.getCardsByUser(userId));
    }

    @PostMapping("/cards/{cardId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateCard(@PathVariable UUID cardId) {
        userServiceImpl.setCardActive(cardId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cards/{cardId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCard(@PathVariable UUID cardId) {
        userServiceImpl.setCardActive(cardId, false);
        return ResponseEntity.noContent().build();
    }
}
