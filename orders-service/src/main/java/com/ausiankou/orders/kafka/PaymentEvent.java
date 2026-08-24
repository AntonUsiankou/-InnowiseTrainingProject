package com.ausiankou.orders.kafka;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors the CREATE_PAYMENT event payload produced by Payment Service. */
public record PaymentEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId,
        String status,          // SUCCESS or FAILED
        BigDecimal paymentAmount
) {}
