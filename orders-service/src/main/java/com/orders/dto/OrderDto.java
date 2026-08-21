package com.orders.dto;

import com.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        UUID userId,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemDto> items,
        UserInfoDto userInfo,
        Instant createdAt,
        Instant updatedAt
) {}
