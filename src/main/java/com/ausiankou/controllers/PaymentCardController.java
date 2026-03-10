package com.ausiankou.controllers;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
@Slf4j
public class PaymentCardController {

    private final UserService userService;

    @Autowired
    public PaymentCardController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentCardDto> getCardById(@PathVariable Long id) {
        log.info("REST-запрос на получение карты по id: {}", id);
        PaymentCardDto cardDto = userService.getCardById(id);
        return ResponseEntity.ok(cardDto);
    }

    @GetMapping
    public ResponseEntity<Page<PaymentCardDto>> getAllCards(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        log.info("REST-запрос на получение всех карт с фильтрами - userId: {}, active: {}",
                userId, active);
        Page<PaymentCardDto> page = userService.getAllCards(userId, active, pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentCardDto> updateCard(
            @PathVariable Long id,
            @Validated(PaymentCardDto.Update.class) @RequestBody PaymentCardDto cardDto) {
        log.info("REST-запрос на обновление карты: {}", id);
        cardDto.setId(id);
        PaymentCardDto result = userService.updateCard(id, cardDto);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateCard(@PathVariable Long id) {
        log.info("REST-запрос на активацию карты: {}", id);
        userService.activateCard(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCard(@PathVariable Long id) {
        log.info("REST-запрос на деактивацию карты: {}", id);
        userService.deactivateCard(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable Long id) {
        log.info("REST-запрос на удаление карты: {}", id);
        userService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }
}