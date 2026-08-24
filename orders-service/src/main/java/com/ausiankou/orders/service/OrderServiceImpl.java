package com.ausiankou.orders.service;

import com.ausiankou.orders.client.UserServiceClient;
import com.ausiankou.orders.dto.*;
import com.ausiankou.orders.dto.*;
import com.ausiankou.orders.dto.*;
import com.ausiankou.orders.entity.Order;
import com.ausiankou.orders.entity.OrderItem;
import com.ausiankou.orders.entity.OrderStatus;
import com.ausiankou.orders.exception.OrderException;
import com.ausiankou.orders.mapper.OrderMapper;
import com.ausiankou.orders.repository.OrderRepository;
import com.ausiankou.orders.repository.spec.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Реализация сервиса управления заказами {@link OrderServiceImpl}.
 * <p>
 * Класс инкапсулирует бизнес-логику обработки заказов, координирует работу
 * с транзакционной базой данных PostgreSQL и осуществляет интеграционные вызовы
 * во внешний микросервис пользователей через Feign/WebClient.
 * </p>
 *
 * @author Ваше Имя
 * @see IOrderService
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderDto createOrder(OrderCreateRequest request) {
        Order order = Order.builder()
                .userId(request.userId())
                .status(OrderStatus.CREATED)
                .totalPrice(BigDecimal.ZERO) // priced by a pricing step against Items in a fuller implementation
                .build();

        List<OrderItem> items = request.items().stream()
                .map(i -> OrderItem.builder().order(order).itemId(i.itemId()).quantity(i.quantity()).build())
                .toList();
        order.setItems(items);

        Order saved = orderRepository.save(order);
        return orderMapper.toDto(saved); // creation response has no user-info enrichment per spec
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id, String requesterEmail) {
        Order order = orderRepository.findById(id).orElseThrow(() -> OrderException.notFound("Order"));
        UserInfoDto userInfo = userServiceClient.getUserByEmail(requesterEmail);
        return orderMapper.toDtoWithUser(order, userInfo);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> searchOrders(Instant from, Instant to, List<OrderStatus> statuses,
                                               String requesterEmail, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(OrderSpecifications.filterBy(from, to, statuses), pageable);
        UserInfoDto userInfo = userServiceClient.getUserByEmail(requesterEmail);
        return PageResponse.from(page.map(o -> orderMapper.toDtoWithUser(o, userInfo)));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersByUser(UUID userId, String requesterEmail) {
        UserInfoDto userInfo = userServiceClient.getUserByEmail(requesterEmail);
        return orderRepository.findAllByUserId(userId).stream()
                .map(o -> orderMapper.toDtoWithUser(o, userInfo))
                .toList();
    }

    @Transactional
    public OrderDto updateOrder(UUID id, OrderUpdateRequest request) {
        Order order = orderRepository.findById(id).orElseThrow(() -> OrderException.notFound("Order"));
        if (request.status() != null) {
            order.setStatus(request.status());
        }
        return orderMapper.toDto(order);
    }

    @Transactional
    public void deleteOrder(UUID id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> OrderException.notFound("Order"));
        order.setDeleted(true); // soft delete
    }
}
