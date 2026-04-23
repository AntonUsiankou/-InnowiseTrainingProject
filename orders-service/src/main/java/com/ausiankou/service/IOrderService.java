package com.ausiankou.service;

import com.ausiankou.dto.CreateOrderRequest;
import com.ausiankou.dto.OrderResponse;
import com.ausiankou.dto.OrderStatusResponse;
import com.ausiankou.dto.UpdateOrderRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления заказами.
 * Предоставляет функционал по созданию, поиску, обновлению и удалению заказов,
 * а также методы для обогащения данных из внешних источников.
 */
public interface IOrderService {
    /**
     * Создает новый заказ на основе предоставленных данных.
     *
     * @param request объект с данными для создания заказа
     * @return созданный заказ в формате {@link OrderResponse}
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * Возвращает информацию о заказе по его уникальному идентификатору.
     *
     * @param id идентификатор заказа
     * @return найденный заказ
     */
    OrderResponse getOrderById(Long id);

    /**
     * Возвращает список заказов с фильтрацией по дате и статусам.
     * Результат предоставляется в виде страницы (пагинация).
     *
     * @param fromDate начальная дата периода поиска
     * @param toDate   конечная дата периода поиска
     * @param statuses список статусов для фильтрации
     * @param pageable параметры пагинации и сортировки
     * @return страница с результатами поиска
     */
    Page<OrderResponse> getOrdersWithFilters(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            List<OrderStatusResponse> statuses,
            Pageable pageable);
    /**
     * Возвращает все заказы конкретного пользователя.
     *
     * @param userId   идентификатор пользователя
     * @param pageable параметры пагинации и сортировки
     * @return страница заказов пользователя
     */
    Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable);

    /**
     * Обновляет данные существующего заказа.
     *
     * @param id      идентификатор обновляемого заказа
     * @param request объект с новыми данными заказа
     * @return обновленный заказ
     */
    OrderResponse updateOrder(Long id, UpdateOrderRequest request);

    /**
     * Удаляет заказ из системы.
     *
     * @param id идентификатор заказа для удаления
     */
    void deleteOrder(Long id);

    /**
     * Обогащает объект ответа информацией о пользователе
     * (например, из сервиса профилей или Keycloak).
     *
     * @param response объект заказа для наполнения данными
     */
    void enrichWithUserInfo(OrderResponse response);

    /**
     * Обогащает объект ответа детальной информацией о товарах
     * (например, названиями, описаниями и актуальными ценами из каталога).
     *
     * @param response объект заказа для наполнения данными
     */
    void enrichWithItemDetails(OrderResponse response);
}
