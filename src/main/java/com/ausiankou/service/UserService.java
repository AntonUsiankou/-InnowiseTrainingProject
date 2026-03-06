package com.ausiankou.service;

import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
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

    @Autowired
    public UserService(UserRepository userRepository,
                       PaymentCardRepository cardRepository){
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
    }

    //CREATE
    public User createUser(User user){
        return userRepository.save(user);
    }

    public PaymentCard createCard(Long userId, PaymentCard card){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        long cardCount = cardRepository.countByUserId(userId);
        if(cardCount >= 5){
            throw new RuntimeException("Пользователь не может иметь более 5 карт");
        }
        card.setUser(user);
        return cardRepository.save(card);
    }

    //GET_BY_ID
    public User getUserById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
    public PaymentCard getCardById(Long id){
        return cardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Карта не найдена"));
    }
    public List<PaymentCard> getCardsByUserId(Long userId) {
        return cardRepository.findByUserId(userId);
    }

    //GET_ALL
    public Page<User> getAllUsers(String name, String surname, Pageable pageable) {
        Specification<User> spec = UserSpecification.nameAndSurname(name, surname);
        return userRepository.findAll(spec, pageable);
    }

    public Page<PaymentCard> getAllCards(Long userId, Boolean active, Pageable pageable) {
        Specification<PaymentCard> spec = Specification
                .where(PaymentCardSpecification.byUserId(userId))
                .and(PaymentCardSpecification.isActive(active));
        return cardRepository.findAll(spec, pageable);
    }

    //UPDATE
    public User updateUser(Long id, User userDetails) {
        User user = getUserById(id);

        user.setName(userDetails.getName());
        user.setSurname(userDetails.getSurname());
        user.setBirthDate(userDetails.getBirthDate());
        user.setEmail(userDetails.getEmail());

        log.info("Обновляем юзера: {}", id);
        return userRepository.save(user);
    }

    public PaymentCard updateCard(Long id, PaymentCard cardDetails) {
        PaymentCard card = getCardById(id);

        card.setNumber(cardDetails.getNumber());
        card.setHolder(cardDetails.getHolder());
        card.setExpirationDate(cardDetails.getExpirationDate());

        log.info("Обновляем карточку: {}", id);
        return cardRepository.save(card);
    }

    //isActive
    @Transactional
    public void activateUser(Long id){
        int updated = userRepository.updateUserStatus(id, true);
        if(updated == 0){
            throw new RuntimeException("Пользователь не найден");
        }
        log.info("Активный пользователь: {}", id);
    }

    @Transactional
    public void deactivateUser(Long id){
        int updated = userRepository.updateUserStatus(id, false);
        if (updated == 0){
            throw new RuntimeException("Пользователь не найден");
        }
        cardRepository.updateCardStatusByUserId(id, false);
        log.info("Не активные юзеры: {}", id);
    }

    @Transactional
    public void activateCard(Long id) {
        int updated = cardRepository.updateCardStatus(id, true);
        if (updated == 0) {
            throw new RuntimeException("Card not found");
        }
        log.info("Активная карта: {}", id);
    }

    @Transactional
    public void deactivateCard(Long id) {
        int updated = cardRepository.updateCardStatus(id, false);
        if (updated == 0) {
            throw new RuntimeException("Card not found");
        }
        log.info("Не активная карта: {}", id);
    }
    //Delete
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
        log.info("Deleted user and all his cards: {}", id);
    }

    public List<User> searchByFullName(String fullName) {
        return userRepository.searchByFullNameNative(fullName);
    }

    public User getUserDetailsWithCards(Long id) {
        User user = getUserById(id);
        List<PaymentCard> cards = cardRepository.findByUserIdAndActiveTrue(id);
        user.setPaymentCards(cards);
        return user;
    }

}
