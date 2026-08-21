package com.orders.dto;

import java.util.UUID;

public record OrderItemDto(UUID id, UUID itemId, int quantity) {}
