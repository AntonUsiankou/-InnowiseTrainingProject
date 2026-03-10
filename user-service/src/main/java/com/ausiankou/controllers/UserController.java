package com.ausiankou.controllers;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Valid @RequestBody UserCreateDto createDto) {
        log.info("Запроос на создание Юзера(котроллер): {}", createDto.getEmail());
        UserDto result = userService.createUser(createDto);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @PostMapping("/{userId}/cards")
    public ResponseEntity<PaymentCardDto> addCardToUser(
            @PathVariable Long userId,
            @Valid @RequestBody PaymentCardDto cardDto) {
        log.info("Запрос на добавление карты юзеру(котроллер): {}", userId);
        cardDto.setUserId(userId);
        PaymentCardDto result = userService.createCard(userId, cardDto);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        log.info("(Контроллер) Поиск по ID: {}", id);
        UserDto userDto = userService.getUserById(id);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String surname,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        log.info("(Контроллер) ользователи по фильтрам Имя: {}, фамилия: {}",
                name, surname);
        Page<UserDto> page = userService.getAllUsers(name, surname, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{userId}/cards")
    public ResponseEntity<List<PaymentCardDto>> getUserCards(@PathVariable Long userId) {
        log.info("(Контроллер) поиск карт по пользователям: {}", userId);
        List<PaymentCardDto> cards = userService.getCardsByUserId(userId);
        return ResponseEntity.ok(cards);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Validated(UserDto.Update.class) @RequestBody UserDto userDto) {
        log.info("(Контроллер) Запрос на обновление: {}", id);
        userDto.setId(id);
        UserDto result = userService.updateUser(id, userDto);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        log.info("(Контроллер) на активацию: {}", id);
        userService.activateUser(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        log.info("(Контроллер) дезактивация юзера: {}", id);
        userService.deactivateUser(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("(Контроллер) Уждаление юзера: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchByFullName(
            @RequestParam String fullName) {
        log.info("(Контроллер) Поиск пользователя по полному имени: {}", fullName);
        List<UserDto> users = userService.searchByFullName(fullName);
        return ResponseEntity.ok(users);
    }
}
