package com.ausiankou.payment.kafka;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId,
        String status,
        BigDecimal paymentAmount
) {}
