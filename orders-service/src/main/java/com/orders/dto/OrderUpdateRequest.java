package com.orders.dto;

import com.orders.entity.OrderStatus;

public record OrderUpdateRequest(OrderStatus status) {}
