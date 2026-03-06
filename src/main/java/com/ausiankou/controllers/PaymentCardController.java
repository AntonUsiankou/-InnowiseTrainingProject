package com.ausiankou.controllers;

import com.ausiankou.entity.PaymentCard;
import com.ausiankou.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
public class PaymentCardController {

    private final UserService userService;

    @Autowired
    public PaymentCardController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentCard> getCardById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getCardById(id));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentCard>> getAllCards(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getAllCards(userId, active, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentCard> updateCard(
            @PathVariable Long id,
            @RequestBody PaymentCard card) {
        return ResponseEntity.ok(userService.updateCard(id, card));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateCard(@PathVariable Long id) {
        userService.activateCard(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCard(@PathVariable Long id) {
        userService.deactivateCard(id);
        return ResponseEntity.ok().build();
    }
}
