package com.ausiankou.ordersservice;


import com.ausiankou.client.UserServiceClientWrapper;
import com.ausiankou.dto.*;
import com.ausiankou.entity.Item;
import com.ausiankou.entity.Order;
import com.ausiankou.entity.OrderStatus;
import com.ausiankou.mapper.OrderMapper;
import com.ausiankou.repository.ItemRepository;
import com.ausiankou.repository.OrderRepository;
import com.ausiankou.service.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;;

    @Mock
    private UserServiceClientWrapper userServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;
    private OrderResponse orderResponse;
    private Item item;
    private UserInfo userInfo;
    private OrderMapper orderMapper;
    @BeforeEach
    void setUp() {
        item = Item.builder()
                .id(1L)
                .name("Test Item")
                .price(BigDecimal.valueOf(100))
                .build();

        order = Order.builder()
                .id(1L)
                .userId(1L)
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(200))
                .deleted(false)
                .build();

        orderResponse = OrderResponse.builder()
                .id(1L)
                .userId(1L)
                .status(OrderStatusResponse.PENDING)
                .totalPrice(BigDecimal.valueOf(200))
                .build();

        userInfo = UserInfo.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
    }

    @Test
    void createOrder_Success() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(2)
                                .build()
                ))
                .build();

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(orderResponse);
        when(userServiceClient.getUserById(1L)).thenReturn(userInfo);

        // When
        OrderResponse result = orderService.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void createOrder_ItemNotFound_ThrowsException() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(999L)
                                .quantity(2)
                                .build()
                ))
                .build();

        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Item not found");
    }

    @Test
    void getOrderById_Success() {
        // Given
        when(orderRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);
        when(userServiceClient.getUserById(1L)).thenReturn(userInfo);

        // When
        OrderResponse result = orderService.getOrderById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(orderRepository, times(1)).findByIdAndDeletedFalse(1L);
    }

    @Test
    void getOrderById_NotFound_ThrowsException() {
        // Given
        when(orderRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void getOrdersWithFilters_Success() {
        // Given
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(orderResponse);
        when(userServiceClient.getUserById(1L)).thenReturn(userInfo);

        // When
        Page<OrderResponse> result = orderService.getOrdersWithFilters(
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now(),
                List.of(OrderStatusResponse.PENDING),
                pageable
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getOrdersByUserId_Success() {
        // Given
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(orderRepository.findByUserIdAndDeletedFalse(1L, pageable)).thenReturn(orderPage);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(orderResponse);
        when(userServiceClient.getUserById(1L)).thenReturn(userInfo);

        // When
        Page<OrderResponse> result = orderService.getOrdersByUserId(1L, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository, times(1)).findByUserIdAndDeletedFalse(1L, pageable);
    }

    @Test
    void updateOrder_Success() {
        // Given
        UpdateOrderRequest request = UpdateOrderRequest.builder()
                .status(OrderStatusResponse.CONFIRMED)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(3)
                                .build()
                ))
                .build();

        when(orderRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(order));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(orderResponse);
        when(userServiceClient.getUserById(1L)).thenReturn(userInfo);

        // When
        OrderResponse result = orderService.updateOrder(1L, request);

        // Then
        assertThat(result).isNotNull();
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void deleteOrder_Success() {
        // Given
        when(orderRepository.existsByIdAndDeletedFalse(1L)).thenReturn(true);
        doNothing().when(orderRepository).softDeleteById(1L);

        // When
        orderService.deleteOrder(1L);

        // Then
        verify(orderRepository, times(1)).softDeleteById(1L);
    }

    @Test
    void deleteOrder_NotFound_ThrowsException() {
        // Given
        when(orderRepository.existsByIdAndDeletedFalse(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> orderService.deleteOrder(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order not found");
    }
}
