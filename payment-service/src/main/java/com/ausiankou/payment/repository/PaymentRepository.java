package com.ausiankou.payment.repository;

import com.ausiankou.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Optional<Payment> findByOrderId(Long orderId);

    Page<Payment> findByStatus(String status, Pageable pageable);

    List<Payment> findByUserIdAndTimestampBetween(Long userId, LocalDateTime from, LocalDateTime to);

    List<Payment> findByTimestampBetween(LocalDateTime from, LocalDateTime to);

    @Query("{ $or: [ " +
            "{ 'user_id': ?0 }, " +
            "{ 'order_id': ?1 }, " +
            "{ 'status': ?2 } " +
            "] }")
    Page<Payment> findByUserIdOrOrderIdOrStatus(Long userId, Long orderId, String status, Pageable pageable);

    long countByStatus(String status);
}
