package com.ausiankou.user.service;

import com.ausiankou.user.dto.*;
import com.ausiankou.user.dto.*;
import com.ausiankou.user.dto.*;
import com.ausiankou.user.entity.PaymentCard;
import com.ausiankou.user.entity.User;
import com.ausiankou.user.exception.UserException;
import com.ausiankou.user.mapper.PaymentCardMapper;
import com.ausiankou.user.mapper.UserMapper;
import com.ausiankou.user.repository.PaymentCardRepository;
import com.ausiankou.user.repository.UserRepository;
import com.ausiankou.user.repository.spec.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Реализация сервиса управления пользователями {@link UserServiceImpl}.
 * <p>
 * Координирует транзакции в репозиториях PostgreSQL, управляет кэшем Redis
 * и обеспечивает бизнес-правила безопасности данных пользователей.
 * </p>
 *
 * @author Антон Усенков
 * @see UserServiceImpl
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService{

    private static final int MAX_CARDS_PER_USER = 5;

    private final UserRepository userRepository;
    private final PaymentCardRepository cardRepository;
    private final UserMapper userMapper;
    private final PaymentCardMapper cardMapper;

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw UserException.emailTaken();
        }
        User user = userMapper.toEntity(request);
        return userMapper.toDto(userRepository.save(user));
    }

    @Cacheable(value = "users", key = "#id")
    @Transactional(readOnly = true)
    public UserDto getUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound("User"));
        return userMapper.toDto(user);
    }

    /** Used by Order Service for enrichment; looked up by email per that service's contract. */
    @Cacheable(value = "usersByEmail", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> UserException.notFound("User"));
        return userMapper.toDto(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> searchUsers(String name, String surname, Pageable pageable) {
        Page<User> page = userRepository.findAll(UserSpecifications.filterBy(name, surname), pageable);
        return PageResponse.from(page.map(userMapper::toDto));
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserDto updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound("User"));
        userMapper.updateEntity(request, user);
        return userMapper.toDto(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void setActive(UUID id, boolean active) {
        User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound("User"));
        user.setActive(active);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw UserException.notFound("User");
        }
        userRepository.deleteById(id); // cascades to cards via entity mapping
    }

    // ---- Cards ----

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public PaymentCardDto addCard(UUID userId, PaymentCardCreateRequest request) {
        // Pessimistic lock on the user row: without it, two concurrent
        // requests can both pass the "under limit" check before either
        // commits, letting a user end up with 6+ active cards.
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> UserException.notFound("User"));
        long activeCount = cardRepository.countByUserIdAndActiveTrue(userId);
        if (activeCount >= MAX_CARDS_PER_USER) {
            throw UserException.tooManyCards();
        }
        PaymentCard card = cardMapper.toEntity(request);
        card.setUser(user);
        return cardMapper.toDto(cardRepository.save(card));
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentCardDto> getCardsByUser(UUID userId) {
        return cardRepository.findAllByUserId(userId).stream().map(cardMapper::toDto).toList();
    }

    @CacheEvict(value = "users", allEntries = true)
    @Transactional
    public void setCardActive(UUID cardId, boolean active) {
        PaymentCard card = cardRepository.findById(cardId).orElseThrow(() -> UserException.notFound("Card"));
        card.setActive(active);
    }
}
