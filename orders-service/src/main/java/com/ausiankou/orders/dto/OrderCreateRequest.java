package com.ausiankou.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderCreateRequest(
        @NotNull UUID userId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(@NotNull UUID itemId, @jakarta.validation.constraints.Min(1) int quantity) {}
}
