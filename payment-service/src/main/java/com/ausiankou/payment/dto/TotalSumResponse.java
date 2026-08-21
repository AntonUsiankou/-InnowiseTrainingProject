package com.ausiankou.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TotalSumResponse(Instant from, Instant to, BigDecimal total) {}
