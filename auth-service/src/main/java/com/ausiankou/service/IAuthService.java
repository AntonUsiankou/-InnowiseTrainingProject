package com.ausiankou.service;

import com.ausiankou.dto.AuthResponse;
import com.ausiankou.dto.LoginRequest;
import com.ausiankou.dto.RegistrationRequest;
import com.ausiankou.dto.ValidateTokenResponse;

/**
 * Интерфейс сервиса аутентификации и авторизации пользователей (Auth Service).
 * <p>
 * Предоставляет высокоуровневые операции для управления жизненным циклом сессий,
 * регистрации учетных записей, валидации токенов доступа и ротации refresh-токенов.
 * Соответствует принципу инверсии зависимостей (DIP) из SOLID.
 *
 * @author com.ausiankou
 * @version 1.0.0
 */
public interface IAuthService {

    /**
     * Регистрирует новые учетные данные пользователя в системе аутентификации.
     * <p>
     * Процесс включает проверку уникальности email, эмуляцию или вызов внешнего
     * микросервиса (User Service) для создания профиля, хеширование пароля и
     * генерацию стартовой пары токенов (Access и Refresh).
     *
     * @param request объект DTO с данными для регистрации (email, пароль, роль)
     * @return {@link AuthResponse} содержащий сгенерированные токены и метаданные сессии
     */
    AuthResponse register(RegistrationRequest request);

    /**
     * Аутентифицирует пользователя в системе по его логину (email) и паролю.
     * <p>
     * Проверяет существование записи, соответствие хеша пароля и статус активности аккаунта.
     * В случае успеха генерирует новые сессионные токены.
     *
     * @param request объект DTO с учетными данными для входа (email, пароль)
     */
    AuthResponse login(LoginRequest request);

    /**
     * Проверяет валидность и срок действия предоставленного JWT-токена доступа (Access Token).
     * <p>
     * Выполняет криптографическую проверку подписи, проверку времени жизни (TTL),
     * а также валидацию статуса пользователя (существование и блокировка) в базе данных.
     *
     * @param token строка JWT-токена без префикса типа (например, без "Bearer ")
     * @return {@link ValidateTokenResponse} со статусом проверки и извлеченными Claims (userId, email, role)
     */
    ValidateTokenResponse validateToken(String token);

    /**
     * Обновляет сессию пользователя (ротация токенов) на основании валидного Refresh-токена.
     * <p>
     * Метод реализует механизм Token Rotation: старый Refresh-токен принудительно
     * аннулируется, а взамен выпускается абсолютно новая пара Access и Refresh токенов.
     *
     * @param refreshToken строка текущего refresh-токена, хранящегося на клиенте
    */
    AuthResponse refreshToken(String refreshToken);

    /**
     * Прекращает сессию пользователя (выход из системы).
     * <p>
     * Принудительно аннулирует и удаляет предоставленный Refresh-токен из хранилища Redis,
     * делая невозможным дальнейшее обновление сессии без повторного ввода пароля.
     *
     * @param refreshToken строка текущего refresh-токена пользователя
     */
    void logout(String refreshToken);
}