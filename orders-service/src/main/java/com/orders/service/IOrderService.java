package com.orders.service;

import com.orders.dto.OrderCreateRequest;
import com.orders.dto.OrderDto;
import com.orders.dto.OrderUpdateRequest;
import com.orders.dto.PageResponse;
import com.orders.entity.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для управления жизненным циклом заказов в высоконагруженной системе.
 * <p>
 * Обеспечивает создание, обновление, мягкое удаление заказов, а также интеграцию
 * с внешним микросервисом пользователей (User Service) для обогащения данных при чтении.
 * </p>
 *
 * @author Ваше Имя
 * @version 1.0
 */
public interface IOrderService {

    /**
     * Создает новый заказ в системе с начальным статусом {@link OrderStatus#CREATED}.
     * <p>
     * На данном этапе данные пользователя не запрашиваются из внешнего сервиса
     * для минимизации Latency при оформлении заказа.
     * </p>
     *
     * @param request DTO с данными для создания заказа (ID пользователя, список позиций)
     * @return {@link OrderDto} созданного заказа без обогащения данными пользователя
     */
    OrderDto createOrder(OrderCreateRequest request);

    /**
     * Получает детальную информацию о заказе по его уникальному идентификатору.
     * <p>
     * Результат обогащается данными профиля пользователя, запрашиваемыми
     * через синхронный REST-клиент из User Service.
     * </p>
     *
     * @param id уникальный идентификатор заказа (UUID)
     * @param requesterEmail email инициатора запроса для проверки прав и получения данных профиля
     * @return {@link OrderDto} с полной информацией о заказе и профиле пользователя
     * @throws RuntimeException если заказ не найден в базе данных
     */
    OrderDto getOrder(UUID id, String requesterEmail);

    /**
     * Выполняет фильтрацию и постраничный поиск заказов по заданным критериям.
     * <p>
     * Использует Spring Data Specifications для динамической сборки SQL-запроса.
     * Данные каждого заказа обогащаются информацией о пользователе из внешнего сервиса.
     * </p>
     *
     * @param from начальная дата диапазона создания заказа
     * @param to конечная дата диапазона создания заказа
     * @param statuses список интересующих статусов заказов для фильтрации
     * @param requesterEmail email инициатора запроса
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return {@link PageResponse} с постраничным списком обогащенных заказов
     */
    PageResponse<OrderDto> searchOrders(Instant from, Instant to, List<OrderStatus> statuses,
                                        String requesterEmail, Pageable pageable);

    /**
     * Возвращает список всех заказов конкретного пользователя.
     * <p>
     * Метод выполняет групповой запрос к БД и обогащает полученные сущности
     * данными профиля из User Service.
     * </p>
     *
     * @param userId уникальный идентификатор пользователя (UUID)
     * @param requesterEmail email инициатора запроса
     * @return список {@link OrderDto}, принадлежащих указанному пользователю
     */
    List<OrderDto> getOrdersByUser(UUID userId, String requesterEmail);

    /**
     * Обновляет параметры существующего заказа (например, его статус).
     *
     * @param id уникальный идентификатор заказа (UUID)
     * @param request DTO с новыми параметрами заказа
     * @return {@link OrderDto} с обновленными данными (без обогащения профилем)
     * @throws RuntimeException если заказ для обновления не найден
     */
    OrderDto updateOrder(UUID id, OrderUpdateRequest request);

    /**
     * Производит мягкое удаление заказа из системы (Soft Delete).
     * <p>
     * Запись не удаляется физически из базы данных, вместо этого флаг
     * {@code deleted} устанавливается в значение {@code true}.
     * </p>
     *
     * @param id уникальный идентификатор удаляемого заказа (UUID)
     * @throws RuntimeException если заказ для удаления не найден
     */
    void deleteOrder(UUID id);
}
