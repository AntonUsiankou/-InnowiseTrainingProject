package com.ausiankou.payment.dto;

import com.ausiankou.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        java.util.UUID id,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        Instant timestamp,
        BigDecimal paymentAmount
) {}
