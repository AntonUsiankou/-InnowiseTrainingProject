package com.orders.auth.ordersservice.service;

import com.orders.client.UserServiceClient;
import com.orders.dto.OrderCreateRequest;
import com.orders.dto.OrderDto;
import com.orders.dto.OrderUpdateRequest;
import com.orders.dto.UserInfoDto;
import com.orders.entity.Order;
import com.orders.entity.OrderStatus;
import com.orders.exception.OrderException;
import com.orders.mapper.OrderMapper;
import com.orders.repository.OrderRepository;
import com.orders.service.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void createOrder_savesOrderWithItems() {
        UUID userId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(userId,
                List.of(new OrderCreateRequest.OrderItemRequest(UUID.randomUUID(), 2)));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderDto dto = new OrderDto(UUID.randomUUID(), userId, OrderStatus.CREATED, BigDecimal.ZERO, List.of(), null, null, null);
        when(orderMapper.toDto(any(Order.class))).thenReturn(dto);

        OrderDto result = orderService.createOrder(request);

        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository).save(any(Order.class));
        // never calls User Service on creation - only on read/enrichment
        verifyNoInteractions(userServiceClient);
    }

    @Test
    void getOrder_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id, "someone@example.com"))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getOrder_usesUserServiceFallback_whenCircuitBreakerTripped() {
        // Simulates the fallback path in UserServiceClient returning the
        // "unavailable" placeholder instead of throwing - the order lookup
        // must still succeed rather than failing the whole request.
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).userId(UUID.randomUUID()).status(OrderStatus.PAID).totalPrice(BigDecimal.TEN).build();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserByEmail("someone@example.com")).thenReturn(UserInfoDto.unavailable());
        OrderDto dto = new OrderDto(id, order.getUserId(), OrderStatus.PAID, BigDecimal.TEN, List.of(), UserInfoDto.unavailable(), null, null);
        when(orderMapper.toDtoWithUser(order, UserInfoDto.unavailable())).thenReturn(dto);

        OrderDto result = orderService.getOrder(id, "someone@example.com");

        assertThat(result.userInfo().name()).isEqualTo("unavailable");
    }

    @Test
    void deleteOrder_softDeletes_doesNotCallRepositoryDelete() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).userId(UUID.randomUUID()).status(OrderStatus.CREATED).totalPrice(BigDecimal.ZERO).build();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        orderService.deleteOrder(id);

        assertThat(order.isDeleted()).isTrue();
        verify(orderRepository, never()).deleteById(any());
        verify(orderRepository, never()).delete((Order) any());
    }

    @Test
    void updateOrder_changesStatus() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).userId(UUID.randomUUID()).status(OrderStatus.CREATED).totalPrice(BigDecimal.ZERO).build();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        OrderDto dto = new OrderDto(id, order.getUserId(), OrderStatus.PAID, BigDecimal.ZERO, List.of(), null, null, null);
        when(orderMapper.toDto(order)).thenReturn(dto);

        OrderDto result = orderService.updateOrder(id, new OrderUpdateRequest(OrderStatus.PAID));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
    }
}
