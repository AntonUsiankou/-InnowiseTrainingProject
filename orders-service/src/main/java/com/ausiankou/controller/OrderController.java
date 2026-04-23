package com.ausiankou.controller;

import com.ausiankou.dto.CreateOrderRequest;
import com.ausiankou.dto.OrderResponse;
import com.ausiankou.dto.OrderStatusResponse;
import com.ausiankou.dto.UpdateOrderRequest;
import com.ausiankou.service.OrderServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST-контроллер для управления заказами.
 * Обеспечивает API для создания, чтения, обновления и удаления заказов.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderServiceImpl orderService;

    /**
     * Создает новый заказ.
     *
     * @param request данные для создания заказа
     * @return созданный заказ со статусом 201 Created
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Получает детальную информацию о заказе по его ID.
     *
     * @param id уникальный идентификатор заказа
     * @return заказ со статусом 200 OK
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Возвращает список заказов с поддержкой фильтрации и пагинации.
     *
     * @param fromDate начальная дата поиска (ISO формат)
     * @param toDate   конечная дата поиска (ISO формат)
     * @param statuses список интересующих статусов заказа
     * @param pageable параметры пагинации (по умолчанию: 20 элементов, сортировка по createdAt DESC)
     * @return страница заказов
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) List<OrderStatusResponse> statuses,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrderResponse> orders = orderService.getOrdersWithFilters(fromDate, toDate, statuses, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Возвращает все заказы конкретного пользователя с пагинацией.
     *
     * @param userId   ID пользователя
     * @param pageable параметры пагинации
     * @return страница заказов пользователя
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrderResponse> orders = orderService.getOrdersByUserId(userId, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Обновляет существующий заказ (статус или состав позиций).
     *
     * @param id      ID заказа
     * @param request новые данные заказа
     * @return обновленный заказ
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {

        OrderResponse response = orderService.updateOrder(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Удаляет заказ (мягкое удаление).
     *
     * @param id ID заказа
     * @return пустой ответ со статусом 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
