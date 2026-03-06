package com.ausiankou.repository;

import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentCardRepository extends JpaRepository<PaymentCard, Long> {
    List<PaymentCard> findByUserId(Long userId);
    Optional<PaymentCard> findByNumber(String number);
    List<PaymentCard> findByUserAndActiveTrue(User user);
}