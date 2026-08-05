package com.ausiankou.payment.controller;

import com.ausiankou.payment.dto.PaymentRequest;
import com.ausiankou.payment.dto.PaymentResponse;
import com.ausiankou.payment.dto.PaymentSummaryResponse;
import com.ausiankou.payment.service.ImplPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final ImplPaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("REST request to create payment for order: {}", request.getOrderId());
        PaymentResponse response = paymentService.createPaymentSync(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable String id) {
        log.info("REST request to get payment by id: {}", id);
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId) {
        log.info("REST request to get payment by orderId: {}", orderId);
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> getPayments(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "timestamp") Pageable pageable) {
        log.info("REST request to get payments - userId: {}, orderId: {}, status: {}", userId, orderId, status);
        Page<PaymentResponse> payments = paymentService.getPayments(userId, orderId, status, pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/summary/user/{userId}")
    public ResponseEntity<PaymentSummaryResponse> getUserPaymentSummary(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        log.info("REST request to get payment summary for user: {} from {} to {}", userId, fromDate, toDate);
        PaymentSummaryResponse response = paymentService.getTotalSumForUser(userId, fromDate, toDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary/all")
    public ResponseEntity<PaymentSummaryResponse> getAllUsersPaymentSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        log.info("REST request to get payment summary for all users from {} to {}", fromDate, toDate);
        PaymentSummaryResponse response = paymentService.getTotalSumForAllUsers(fromDate, toDate);
        return ResponseEntity.ok(response);
    }
}
