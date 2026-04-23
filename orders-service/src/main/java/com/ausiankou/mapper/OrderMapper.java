package com.ausiankou.mapper;


import com.ausiankou.dto.CreateOrderRequest;
import com.ausiankou.dto.ItemInfo;
import com.ausiankou.dto.OrderItemResponse;
import com.ausiankou.dto.OrderResponse;
import com.ausiankou.entity.Item;
import com.ausiankou.entity.Order;
import com.ausiankou.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    Order toEntity(CreateOrderRequest request);

    @Mapping(target = "status", source = "status")
    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "userInfo", ignore = true)
    OrderResponse toResponse(Order order);

    @Mapping(target = "item", source = "item")
    @Mapping(target = "subtotal", expression = "java(orderItem.getItem().getPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())))")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    ItemInfo toItemInfo(Item item);
}
