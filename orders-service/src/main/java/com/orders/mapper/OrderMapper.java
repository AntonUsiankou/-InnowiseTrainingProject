package com.orders.mapper;

import com.orders.dto.OrderDto;
import com.orders.dto.OrderItemDto;
import com.orders.dto.UserInfoDto;
import com.orders.entity.Order;
import com.orders.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderItemDto toDto(OrderItem item);

    @Mapping(target = "userInfo", ignore = true)
    OrderDto toDto(Order order);

    default OrderDto toDtoWithUser(Order order, UserInfoDto userInfo) {
        OrderDto base = toDto(order);
        return new OrderDto(base.id(), base.userId(), base.status(), base.totalPrice(),
                base.items(), userInfo, base.createdAt(), base.updatedAt());
    }
}
