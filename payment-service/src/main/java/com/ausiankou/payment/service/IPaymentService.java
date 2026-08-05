package com.ausiankou.payment.service;

import com.ausiankou.payment.dto.PaymentRequest;
import com.ausiankou.payment.dto.PaymentResponse;
import com.ausiankou.payment.dto.PaymentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public interface IPaymentService {

    CompletableFuture<PaymentResponse> createPaymentAsync(PaymentRequest request);
    PaymentResponse createPaymentSync(PaymentRequest request);
    PaymentResponse getPaymentById(String id);
    PaymentResponse getPaymentByOrderId(Long orderId);
    Page<PaymentResponse> getPayments(Long userId,
                                      Long orderId,
                                      String status,
                                      Pageable pageable);
    PaymentSummaryResponse getTotalSumForUser(Long userId, LocalDateTime fromDate, LocalDateTime toDate);
    PaymentSummaryResponse getTotalSumForAllUsers(LocalDateTime fromDate, LocalDateTime toDate);
}
