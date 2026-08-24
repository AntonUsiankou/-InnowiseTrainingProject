package com.ausiankou.orders.mapper;

import com.ausiankou.orders.dto.OrderDto;
import com.ausiankou.orders.dto.OrderItemDto;
import com.ausiankou.orders.dto.UserInfoDto;
import com.ausiankou.orders.entity.Order;
import com.ausiankou.orders.entity.OrderItem;
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
