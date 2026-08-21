package com.ausiankou.payment.controller;

import com.ausiankou.payment.dto.PageResponse;
import com.ausiankou.payment.dto.PaymentCreateRequest;
import com.ausiankou.payment.dto.PaymentDto;
import com.ausiankou.payment.dto.TotalSumResponse;
import com.ausiankou.payment.entity.PaymentStatus;
import com.ausiankou.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PaymentDto>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) PaymentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(paymentService.search(userId, orderId, status, pageable));
    }

    @GetMapping("/users/{userId}/total")
    public ResponseEntity<TotalSumResponse> totalForUser(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(paymentService.totalForUser(userId, from, to));
    }

    @GetMapping("/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TotalSumResponse> totalForAllUsers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(paymentService.totalForAllUsers(from, to));
    }
}
