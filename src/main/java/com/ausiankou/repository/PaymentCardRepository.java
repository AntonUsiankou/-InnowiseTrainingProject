package com.ausiankou.repository;

import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentCardRepository extends JpaRepository<PaymentCard, Long>,
                                                JpaSpecificationExecutor<PaymentCard> {
    List<PaymentCard> findByUserId(Long userId);
    Optional<PaymentCard> findByNumber(String number);
    List<PaymentCard> findByUserIdAndActiveTrue(Long userId);
    @Modifying
    @Query("UPDATE PaymentCard c SET c.active = :active WHERE c.id = :id")
    int updateCardStatus(@Param("id") Long id,
                         @Param("active") Boolean active);
    @Modifying
    @Query("UPDATE PaymentCard c SET c.active = :active WHERE c.user.id = :userId")
    int updateCardStatusByUserId(@Param("userId") Long userId,
                                 @Param("active") Boolean active);
    @Query(value = "SELECT * FROM payment_cards WHERE expiration_date < CURRENT_DATE",
            nativeQuery = true)
    List<PaymentCard> findExpiredCardsNative();
    @Query("SELECT count(c) FROM PaymentCard c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}