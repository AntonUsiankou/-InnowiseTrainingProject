package com.ausiankou.orders.dto;

import com.ausiankou.orders.entity.OrderStatus;

public record OrderUpdateRequest(OrderStatus status) {}
