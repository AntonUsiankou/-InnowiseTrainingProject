package com.ausiankou.user.repository;

import com.ausiankou.user.entity.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {

    List<PaymentCard> findAllByUserId(UUID userId);

    long countByUserIdAndActiveTrue(UUID userId);

    @Query("select c from PaymentCard c where c.user.id = :userId and c.active = true")
    List<PaymentCard> findActiveCardsByUserId(@Param("userId") UUID userId);
}
