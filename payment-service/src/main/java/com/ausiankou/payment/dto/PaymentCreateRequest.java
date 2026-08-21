package com.ausiankou.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreateRequest(
        @NotNull UUID orderId,
        @NotNull UUID userId,
        @NotNull @DecimalMin("0.01") BigDecimal paymentAmount
) {}
