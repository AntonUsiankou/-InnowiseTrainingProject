package com.ausiankou.payment.repository;

import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends MongoRepository<Payment, UUID> {

    Page<Payment> findAllByUserId(UUID userId, Pageable pageable);

    Page<Payment> findAllByOrderId(UUID orderId, Pageable pageable);

    Page<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);

    List<Payment> findAllByUserIdAndTimestampBetween(UUID userId, java.time.Instant from, java.time.Instant to);

    List<Payment> findAllByTimestampBetween(java.time.Instant from, java.time.Instant to);
}
