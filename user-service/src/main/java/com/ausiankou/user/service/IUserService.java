package com.ausiankou.user.service;

import com.ausiankou.dto.*;
import com.ausiankou.user.dto.*;
import com.user.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для управления жизненным циклом пользователей и их платежных карт.
 * <p>
 * Обеспечивает процессы регистрации, обновления профилей, кэширования данных в Redis,
 * мягкого управления активностью учетных записей, а также безопасного добавления карт
 * в условиях высокой конкурентности (Highload).
 * </p>
 *
 * @author Anton Usiankou
 * @version 1.0
 */
public interface IUserService {

    /**
     * Регистрирует нового пользователя в системе.
     *
     * @param request DTO с данными для создания пользователя (email, имя, фамилия)
     * @return {@link UserDto} созданного пользователя с присвоенным уникальным ID
     * @throws RuntimeException если пользователь с таким email уже существует
     */
    UserDto createUser(UserCreateRequest request);

    /**
     * Получает профиль пользователя по его уникальному идентификатору (UUID).
     * <p>
     * Метод кэшируется в Redis. При повторном запросе данные берутся из кэша.
     * </p>
     *
     * @param id уникальный идентификатор пользователя
     * @return {@link UserDto} с данными профиля
     * @throws RuntimeException если пользователь не найден
     */
    UserDto getUser(UUID id);

    /**
     * Получает профиль пользователя по его email.
     * <p>
     * Метод используется микросервисом заказов (Order Service) для обогащения данных.
     * Результат кэшируется в Redis по ключу email.
     * </p>
     *
     * @param email уникальный email пользователя
     * @return {@link UserDto} с данными профиля
     * @throws RuntimeException если пользователь не найден
     */
    UserDto getUserByEmail(String email);

    /**
     * Выполняет постраничный поиск пользователей по имени и/или фамилии.
     *
     * @param name имя для фильтрации (может быть null)
     * @param surname фамилия для фильтрации (может быть null)
     * @param pageable параметры пагинации и сортировки
     * @return {@link PageResponse} с постраничным списком пользователей
     */
    PageResponse<UserDto> searchUsers(String name, String surname, Pageable pageable);

    /**
     * Обновляет персональные данные пользователя.
     * <p>
     * При успешном обновлении соответствующая запись в кэше Redis инвалидируется.
     * </p>
     *
     * @param id уникальный идентификатор пользователя
     * @param request DTO с новыми параметрами профиля
     * @return {@link UserDto} с обновленными данными
     * @throws RuntimeException если пользователь не найден
     */
    UserDto updateUser(UUID id, UserUpdateRequest request);

    /**
     * Изменяет статус активности пользователя (блокировка/разблокировка).
     * <p>
     * Метод инвалидирует кэш пользователя в Redis.
     * </p>
     *
     * @param id уникальный идентификатор пользователя
     * @param active флаг активности
     * @throws RuntimeException если пользователь не найден
     */
    void setActive(UUID id, boolean active);

    /**
     * Удаляет пользователя из системы.
     * <p>
     * Удаление каскадно распространяется на все привязанные карты пользователя.
     * Запись в кэше Redis инвалидируется.
     * </p>
     *
     * @param id уникальный идентификатор удаляемого пользователя
     * @throws RuntimeException если пользователь не найден
     */
    void deleteUser(UUID id);

    /**
     * Безопасно привязывает новую платежную карту к профилю пользователя.
     * <p>
     * Метод использует пессимистическую блокировку строки пользователя в БД
     * для предотвращения Race Condition при проверке лимита карт.
     * </p>
     *
     * @param userId уникальный идентификатор пользователя
     * @param request DTO с реквизитами создаваемой карты
     * @return {@link PaymentCardDto} созданной карты
     * @throws RuntimeException если превышен лимит карт на одного пользователя
     */
    PaymentCardDto addCard(UUID userId, PaymentCardCreateRequest request);

    /**
     * Возвращает список всех карт, привязанных к конкретному пользователю.
     *
     * @param userId уникальный идентификатор пользователя
     * @return список {@link PaymentCardDto}
     */
    List<PaymentCardDto> getCardsByUser(UUID userId);

    /**
     * Изменяет статус активности конкретной платежной карты.
     * <p>
     * Метод приводит к полной очистке кэша пользователей во избежание десинхронизации.
     * </p>
     *
     * @param cardId уникальный идентификатор карты
     * @param active флаг активности карты
     * @throws RuntimeException если карта не найдена
     */
    void setCardActive(UUID cardId, boolean active);
}
