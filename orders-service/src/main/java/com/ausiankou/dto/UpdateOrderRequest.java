package com.ausiankou.dto;

import com.ausiankou.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
    private OrderStatusResponse status;
    private List<OrderItemRequest> items;
}
