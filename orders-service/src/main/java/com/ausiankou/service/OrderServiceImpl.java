package com.ausiankou.service;

import com.ausiankou.client.UserServiceClientWrapper;
import com.ausiankou.dto.*;
import com.ausiankou.entity.Item;
import com.ausiankou.entity.Order;
import com.ausiankou.entity.OrderItem;
import com.ausiankou.entity.OrderStatus;
import com.ausiankou.mapper.OrderMapper;
import com.ausiankou.repository.ItemRepository;
import com.ausiankou.repository.OrderItemRepository;
import com.ausiankou.repository.OrderRepository;
import com.ausiankou.repository.specification.OrderSpecification;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements IOrderService{

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClientWrapper userServiceClient;

    /**
     * Создает новый заказ в статусе PENDING и рассчитывает его общую стоимость.
     * <p>
     * Процесс создания включает:
     * <ul>
     *     <li>Инициализацию заказа со стартовым статусом;</li>
     *     <li>Проверку существования каждого товара в БД;</li>
     *     <li>Расчет итоговой суммы на основе актуальных цен товаров;</li>
     *     <li>Сохранение заказа и связей с позициями (OrderItems);</li>
     *     <li>Обогащение финального ответа данными о пользователе и деталями товаров.</li>
     * </ul>
     *
     * @param request объект с ID пользователя и списком позиций (ID товара и количество)
     * @return {@link OrderResponse} с полными данными, включая внешние интеграции
     * @throws RuntimeException если хотя бы один из указанных товаров не найден в базе данных
     */
    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());

        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemRequest -> {
                    Item item = itemRepository.findById(itemRequest.getItemId())
                            .orElseThrow(() -> new EntityNotFoundException("Item not found: " + itemRequest.getItemId()));

                    return OrderItem.builder()
                            .item(item)
                            .quantity(itemRequest.getQuantity())
                            .build();
                })
                .toList();

        BigDecimal totalPrice = orderItems.stream()
                .map(oi -> oi.getItem().getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalPrice(totalPrice)
                .deleted(false)
                .build();

        orderItems.forEach(item -> {
            item.setOrder(order);
            order.getOrderItems().add(item);
        });

        Order savedOrder = orderRepository.save(order);

        OrderResponse response = orderMapper.toResponse(savedOrder);
        enrichWithUserInfo(response);
        enrichWithItemDetails(response);
        return response;
    }

    /**
     * Возвращает детализированную информацию о заказе по его идентификатору.
     * <p>
     * Метод выполняет следующие действия:
     * <ul>
     *     <li>Поиск активного заказа (не помеченного как удаленный) в БД;</li>
     *     <li>Маппинг сущности в базовый DTO;</li>
     *     <li>Обогащение DTO данными о пользователе и деталями товаров через внешние вызовы.</li>
     * </ul>
     *
     * @param id уникальный идентификатор заказа
     * @return {@link OrderResponse} с полными данными о заказе
     * @throws EntityNotFoundException если заказ с таким ID не найден или помечен как удаленный
     */
    @Transactional(readOnly = true)
    @Override
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        OrderResponse response = orderMapper.toResponse(order);
        enrichWithUserInfo(response);
        enrichWithItemDetails(response);
        return response;
    }

    /**
     * Возвращает страницу заказов, соответствующих заданным критериям фильтрации.
     * <p>
     * Фильтрация осуществляется по следующим правилам:
     * <ul>
     *     <li>Исключаются заказы, помеченные как удаленные;</li>
     *     <li>Если указаны даты, выбираются заказы в интервале [fromDate, toDate];</li>
     *     <li>Если указан список статусов, выбираются заказы только с этими статусами.</li>
     * </ul>
     * После загрузки данных из БД, каждый объект ответа обогащается информацией о пользователе и товарах.
     *
     * @param fromDate начальная дата для фильтрации (может быть null)
     * @param toDate   конечная дата для фильтрации (может быть null)
     * @param statuses список статусов для фильтрации (может быть null)
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница {@link Page} с обогащенными объектами {@link OrderResponse}
     */
    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getOrdersWithFilters(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            List<OrderStatusResponse> statuses,
            Pageable pageable) {
        List<OrderStatus> orderStatuses = statuses != null ?
                statuses.stream().map(s -> OrderStatus.valueOf(s.name())).collect(Collectors.toList()) : null;
        Specification<Order> spec = Specification
                .where(OrderSpecification.notDeleted())
                .and(OrderSpecification.filterByDateRange(fromDate, toDate))
                .and(OrderSpecification.filterByStatuses(orderStatuses));
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        return orders.map(order -> {
            OrderResponse response = orderMapper.toResponse(order);
            enrichWithUserInfo(response);
            enrichWithItemDetails(response);
            return response;
        });
    }

    /**
     * Возвращает страницу заказов конкретного пользователя.
     * <p>
     * Метод оптимизирован для микросервисной архитектуры:
     * <ul>
     *     <li>Загружает заказы пользователя из локальной БД;</li>
     *     <li>Собирает все уникальные ID товаров из всех заказов на странице;</li>
     *     <li>Выполняет пакетное обогащение данных (один запрос к сервису пользователей
     *         и один запрос к данным о товарах) для минимизации сетевых задержек.</li>
     * </ul>
     *
     * @param userId   идентификатор пользователя
     * @param pageable параметры пагинации и сортировки
     * @return страница {@link Page} с детальной информацией о заказах
     */
    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdAndDeletedFalse(userId, pageable);

        return orders.map(order -> {
            OrderResponse response = orderMapper.toResponse(order);
            enrichWithUserInfo(response);
            enrichWithItemDetails(response);
            return response;
        });
    }

    /**
     * Обновляет данные существующего заказа.
     * <p>
     * Особенности процесса обновления:
     * <ul>
     *     <li>Если в запросе передан новый статус, он обновляется;</li>
     *     <li>При передаче нового списка товаров старые позиции заказа полностью удаляются
     *         (благодаря orphanRemoval = true) и заменяются новыми;</li>
     *     <li>При обновлении позиций происходит автоматический пересчет итоговой суммы заказа;</li>
     *     <li>После сохранения выполняется полное обогащение данных через внешние сервисы.</li>
     * </ul>
     *
     * @param id      идентификатор заказа для обновления
     * @param request объект с новыми данными (статус, список товаров)
     * @return {@link OrderResponse} с обновленными и обогащенными данными
     * @throws EntityNotFoundException если заказ не найден или один из новых товаров отсутствует в БД
     */
    @Transactional
    @Override
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        Order order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        if(request.getStatus() != null){
            order.setStatus(OrderStatus.valueOf(request.getStatus().name()));
        }
        if(request.getItems() != null && !request.getItems().isEmpty()) {
            order.getOrderItems().clear();
            BigDecimal totalPrice = BigDecimal.ZERO;
            for (OrderItemRequest itemRequest : request.getItems()) {
                Item item = itemRepository.findById(itemRequest.getItemId())
                        .orElseThrow(() -> new EntityNotFoundException("Item not found: " + itemRequest.getItemId()));
                OrderItem orderItem = OrderItem.builder()
                        .item(item)
                        .quantity(itemRequest.getQuantity())
                        .build();
                order.addOrderItem(orderItem);
                totalPrice = totalPrice.add(item.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
            }
            order.setTotalPrice(totalPrice);
        }
        Order updateOrder = orderRepository.save(order);

        OrderResponse response = orderMapper.toResponse(updateOrder);
        enrichWithUserInfo(response);
        enrichWithItemDetails(response);

        return response;
    }

    /**
     * Выполняет мягкое удаление заказа (Soft Delete).
     * <p>
     * Вместо физического удаления записи из базы данных, метод устанавливает флаг
     * {@code deleted = true}. Удаленные заказы перестают отображаться в обычных
     * выборках, но сохраняются в системе для истории или аудита.
     *
     * @param id уникальный идентификатор заказа
     * @throws EntityNotFoundException если активный заказ с таким ID не найден
     */
    @Override
    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsByIdAndDeletedFalse(id)) {
            throw new EntityNotFoundException("Order not found: " + id);
        }
        orderRepository.softDeleteById(id);
        log.info("Soft deleted order with ID: {}", id);
    }

    /**
     * Обогащает ответ информацией о пользователе, запрашивая данные из внешнего микросервиса.
     * <p>
     * В случае сетевой ошибки или недоступности сервиса пользователей, метод предотвращает
     * падение всего процесса и устанавливает объект-заглушку (Fallback) с пометкой "N/A".
     *
     * @param response объект ответа заказа, в который будет добавлена информация о пользователе
     */
    @Override
    public void enrichWithUserInfo(OrderResponse response) {
        try {
            UserInfo userInfo = userServiceClient.getUserById(response.getUserId());
            response.setUserInfo(userInfo);
        } catch (Exception e) {
            log.error("Failed to fetch user info for ID: {}", response.getUserId(), e);
            response.setUserInfo(UserInfo.builder()
                    .id(response.getUserId())
                    .email("unavailable@example.com")
                    .firstName("N/A")
                    .lastName("N/A")
                    .build());
        }
    }

    /**
     * Рассчитывает промежуточные итоги (subtotal) для каждой позиции в заказе.
     * <p>
     * Проходит по списку товаров и вычисляет стоимость строки как (цена товара * количество).
     * Результат устанавливается в поле {@code subtotal} каждой позиции.
     *
     * @param response объект ответа заказа для расчета стоимостей позиций
     */
    @Override
    public void enrichWithItemDetails(OrderResponse response) {
        if (response.getItems() != null) {
            response.getItems().forEach(itemResponse -> {
                if (itemResponse.getItem() != null) {
                    BigDecimal subtotal = itemResponse.getItem().getPrice()
                            .multiply(BigDecimal.valueOf(itemResponse.getQuantity()));
                    itemResponse.setSubtotal(subtotal);
                }
            });
        }
    }

    @Transactional
    public void updateOrderStatusFromPayment(Long orderId, boolean paymentSuccess) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (paymentSuccess) {
            order.setStatus(OrderStatus.PAID);
            log.info("Order {} status updated to PAID", orderId);
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            log.info("Order {} status updated to PAYMENT_FAILED", orderId);
        }

        orderRepository.save(order);
    }
}
