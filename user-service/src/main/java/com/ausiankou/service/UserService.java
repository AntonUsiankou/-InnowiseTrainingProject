package com.ausiankou.service;

import com.ausiankou.dto.AuthUserDto;
import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
import com.ausiankou.exception.CustomExceptions;
import com.ausiankou.mapper.PaymentCardMapper;
import com.ausiankou.mapper.UserMapper;
import com.ausiankou.repository.PaymentCardRepository;
import com.ausiankou.repository.UserRepository;
import com.ausiankou.repository.specifiactions.PaymentCardSpecification;
import com.ausiankou.repository.specifiactions.UserSpecification;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PaymentCardRepository cardRepository;
    private final UserMapper userMapper;
    private final PaymentCardMapper cardMapper;
    private final CacheManager cacheManager;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                       PaymentCardRepository cardRepository,
                       UserMapper userMapper,
                       PaymentCardMapper cardMapper,
                       CacheManager cacheManager,
                       PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
        this.userMapper = userMapper;
        this.cardMapper = cardMapper;
        this.cacheManager = cacheManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", allEntries = true),
            @CacheEvict(value = "userCards", allEntries = true)
    })
    public UserDto createUser(UserCreateDto createDto){
        if(userRepository.findByEmail(createDto.getEmail()).isPresent()){
            throw new CustomExceptions.DuplicateResourceException(
                    "Пользователь", "email", createDto.getEmail());        }
        User user = userMapper.toEntity(createDto);
        user.setPassword(passwordEncoder.encode(createDto.getPassword()));
        User savedUser = userRepository.save(user);
        log.info("Юзер создан с айдишкой: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userCards", key = "#userId"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public PaymentCardDto createCard(Long userId, PaymentCardDto cardDto){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователь", "id", userId));
        long cardCount = cardRepository.countByUserId(userId);
        if(cardCount >= 5){
            throw  new CustomExceptions.BusinessRuleException(
                    "MAX_CARDS",
                    String.format("Юзер не может иметь больше 5 карт. Текущее количество: %d", cardCount));
        }
        if(cardRepository.findByNumber(cardDto.getNumber()).isPresent()){
            throw new CustomExceptions.DuplicateResourceException(
                    "Карта", "номер", cardDto.getNumber());        }
        PaymentCard card = cardMapper.toEntityWithUser(cardDto, user);
        PaymentCard savedCard = cardRepository.save(card);
        log.info("Карточка созддана успешно отноительно айдишки: {} для юзера: {}", savedCard.getId(), userId);
        return cardMapper.toDto(savedCard);

    }

    @Cacheable(value = "users", key = "#id")
    public UserDto getUserById(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователь", "id", id));
        return userMapper.toDto(user);
    }
    @Cacheable(value = "cards", key = "#id")
    public PaymentCardDto getCardById(Long id){
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователь", "id", id));
        return cardMapper.toDto(card);
    }
    @Cacheable(value = "userCards", key = "#userId")
    public List<PaymentCardDto> getCardsByUserId(Long userId) {
        if(!userRepository.existsById(userId)){
            throw new CustomExceptions.ResourceNotFoundException(
                    "Пользователь", "id", userId);
        }
        List<PaymentCard> cards = cardRepository.findByUserId(userId);
        return cardMapper.toDtoList(cards);
    }

    public Page<UserDto> getAllUsers(String name,
                                  String surname,
                                  Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecification.hasName(name))
                .and(UserSpecification.hasSurname(surname));
        Page<User> usersPage = userRepository.findAll(spec, pageable);
        return usersPage.map(userMapper::toDto);
    }

    public Page<PaymentCardDto> getAllCards(Long userId,
                                         Boolean active,
                                         Pageable pageable) {
        Specification<PaymentCard> spec = Specification
                .where(PaymentCardSpecification.byUserId(userId))
                .and(PaymentCardSpecification.isActive(active));
        Page<PaymentCard> cardsPage = cardRepository.findAll(spec, pageable);
        return cardsPage.map(cardMapper::toDto);
    }

    @Transactional
    @Caching(
            put = {
                    @CachePut(value = "users", key = "#result.id")
            },
            evict = {
                    @CacheEvict(value = "userCards", key = "#id"),
                    @CacheEvict(value = "cards", allEntries = true)
            }
    )
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользоваетль", "id", id));
        if(!user.getEmail().equals(userDto.getEmail())){
            if(userRepository.findByEmail(userDto.getEmail()).isPresent()){
                throw new CustomExceptions.DuplicateResourceException(
                        "Пользователь", "email", userDto.getEmail());
            }
        }
        userMapper.partialUpdate(userDto, user);
        User updatedUser = userRepository.save(user);
        log.info("Успешно обновлен пользователь с Id: {}", id);
        return userMapper.toDto(updatedUser);
    }

    @Transactional
    @Caching(
            put = {
                    @CachePut(value = "cards", key = "#result.id")
            },
            evict = {
                    @CacheEvict(value = "userCards", key = "#result.userId"),
                    @CacheEvict(value = "users", key = "#result.userId")
            }
    )
    public PaymentCardDto updateCard(Long id, PaymentCardDto cardDto) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Карта", "id", id));
        if(!card.getNumber().equals(cardDto.getNumber())){
            if(cardRepository.findByNumber(cardDto.getNumber()).isPresent()){
                throw new CustomExceptions.DuplicateResourceException(
                        "Карта", "номер", cardDto.getNumber());
            }
        }
        cardMapper.updatedEntityFromDto(cardDto, card);
        PaymentCard updatedCard = cardRepository.save(card);
        log.info("Карта обновлена успешно: {}", id);
        return cardMapper.toDto(updatedCard);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void activateUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователи", "id", id));
        if (user.getActive()) {
            log.warn("Пользователь {} уже активен", id);
            throw new CustomExceptions.BusinessRuleException(
                    "ALREADY_ACTIVE", String.format("Пользователь с id %d уже активен", id));
        }
        user.setActive(true);
        userRepository.save(user);
        log.info("Активный пользователь: {}", id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void deactivateUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователи", "id", id));
        if(!user.getActive()){
            log.warn("Пользователь {} уже не активен", id);
            throw new CustomExceptions.BusinessRuleException(
                    "ALREADY_INACTIVE", String.format("Польщователь с id %d уже активен", id));
        }
        user.setActive(false);
        userRepository.save(user);
        cardRepository.updateCardStatusByUserId(id, false);
        log.info("Пользователь и его карты деактивированы: {}", id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void activateCard(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Карта", "id", id));
        if(card.getActive()){
            log.warn("Карта {} уже активна", id);
            throw new CustomExceptions.BusinessRuleException(
                    "ALREADY_ACTIVE", String.format("Карта с id %d уже активен", id));
        }
        card.setActive(true);
        cardRepository.save(card);
        log.info("Карта активирована: {}", id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void deactivateCard(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Карта", "id", id));
        if (!card.getActive()) {
            log.warn("Карта {} уже не активна", id);
            throw new CustomExceptions.BusinessRuleException(
                    "ALREADY_INACTIVE", String.format("Карта с id %d уже активен", id));
        }
        card.setActive(false);
        cardRepository.save(card);
        log.info("Карта дизактивирована: {}", id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                        .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("Пользователи", "id", id));
        userRepository.delete(user);
        log.info("Удаление пользователя и всех его карт: {}", id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userCards", key = "#id"),
            @CacheEvict(value = "cards", allEntries = true)
    })
    public void deleteCard(Long id){
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("Карта", "id", id));
        cardRepository.delete(card);
        log.info("Карта удалена: {}", id);
    }

    public List<UserDto> searchByFullName(String fullName) {
        log.debug("Поиск пользователей по полному имени: {}", fullName);
        List<User> users = userRepository.searchByFullNameNative(fullName);
        if(users.isEmpty()){
            throw new CustomExceptions.ResourceNotFoundException("Пользователи", "имени", fullName);
        }
        return userMapper.toDtoList(users);
    }

    public void clearAllCaches(){
        log.info("Очистка всех кешей");
        cacheManager.getCacheNames().stream()
                .forEach(cacheName -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if(cache != null){
                        cache.clear();
                        log.info("Кеш очищен {}", cacheName);
                    }
                });
    }

    public AuthUserDto getAuthUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("User", "email", email));

        return AuthUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
                .role(user.getRole().name())
                .active(user.getActive())
                .build();
    }

    public AuthUserDto getAuthUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("User", "id", id));

        return AuthUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
                .role(user.getRole().name())
                .active(user.getActive())
                .build();
    }
}