package com.ausiankou.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Autowired
    public UserService(UserRepository userRepository,
                       PaymentCardRepository cardRepository,
                       UserMapper userMapper,
                       PaymentCardMapper cardMapper){
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
        this.userMapper = userMapper;
        this.cardMapper = cardMapper;
    }

    //CREATE
    @Transactional
    public UserDto createUser(UserCreateDto createDto){
        if(userRepository.findByEmail(createDto.getEmail()).isPresent()){
            throw new CustomExceptions.DuplicateResourceException(
                    "Пользователь", "email", createDto.getEmail());        }
        User user = userMapper.toEntity(createDto);
        User savedUser = userRepository.save(user);
        log.info("Юзер создан с айдишкой: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    @Transactional
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

    //GET_BY_ID
    public UserDto getUserById(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователь", "id", id));
        return userMapper.toDto(user);
    }
    public PaymentCardDto getCardById(Long id){
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователь", "id", id));
        return cardMapper.toDto(card);
    }
    public List<PaymentCardDto> getCardsByUserId(Long userId) {
        if(!userRepository.existsById(userId)){
            throw new CustomExceptions.ResourceNotFoundException(
                    "Пользователь", "id", userId);
        }
        List<PaymentCard> cards = cardRepository.findByUserId(userId);
        return cardMapper.toDtoList(cards);
    }

    //GET_ALL
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

    //UPDATE
    @Transactional
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

    //isActive
    @Transactional
    public void activateUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователи", "id", id));
        if (user.getActive()) {
            log.warn("Пользователь {} уже активен", id);
            return;
        }
        user.setActive(true);
        userRepository.save(user);
        log.info("Активный пользователь: {}", id);
    }

    @Transactional
    public void deactivateUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Пользователи", "id", id));
        if(!user.getActive()){
            log.warn("Пользователь {} уже не активен", id);
            return;
        }
        user.setActive(false);
        userRepository.save(user);
        cardRepository.updateCardStatusByUserId(id, false);
        log.info("Пользователь и его карты деактивированы: {}", id);
    }

    @Transactional
    public void activateCard(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Карта", "id", id));
        if(card.getActive()){
            log.warn("Карта {} уже активна", id);
            return;
        }
        card.setActive(true);
        cardRepository.save(card);
        log.info("Карта активирована: {}", id);
    }

    @Transactional
    public void deactivateCard(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException(
                        "Карта", "id", id));
        if (!card.getActive()) {
            log.warn("Карта {} уже не активна", id);
            return;
        }
        card.setActive(false);
        cardRepository.save(card);
        log.info("Карта дизактивирована: {}", id);
    }
    //Delete
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                        .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("Пользователи", "id", id));
        userRepository.delete(user);
        log.info("Удаление пользователя и всех его карт: {}", id);
    }
    @Transactional
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

    public UserDto getUserDetailsWithCards(Long id) {
        UserDto userDto = getUserById(id);
        List<PaymentCard> cards = cardRepository.findByUserIdAndActiveTrue(id);
        List<PaymentCardDto> cardDtos = cardMapper.toDtoList(cards);
        userDto.setPaymentCards(cardDtos);
        return userDto;
    }
    // JPQL
    public List<Object[]> getUsersWithCardCount(Boolean active) {
        if (active == null) {
            throw new CustomExceptions.InvalidDataException("active", "Статус активности должен быть указан (true/false)");
        }
        List<Object[]> results = userRepository.findUsersWithCardCount(active);
        if (results.isEmpty()) {
            throw new CustomExceptions.ResourceNotFoundException("Отчет", "статусу", active);
        }
        return results;
    }
}