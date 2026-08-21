package com.ausiankou.payment.service;

import com.ausiankou.payment.entity.OutboxEvent;
import com.ausiankou.payment.entity.OutboxStatus;
import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.entity.PaymentStatus;
import com.ausiankou.payment.kafka.PaymentEvent;
import com.ausiankou.payment.repository.OutboxRepository;
import com.ausiankou.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately a separate bean from PaymentService: the random-number call
 * to the external API happens BEFORE this is invoked (see
 * PaymentService.createPayment), so the Mongo transaction below only ever
 * spans the two fast local writes, not a network round-trip to a 3rd party.
 * Keeping it in the same class wouldn't work anyway - @Transactional relies
 * on a Spring AOP proxy, which self-invocation (calling another method on
 * `this`) bypasses.
 */
@Component
@RequiredArgsConstructor
class PaymentPersister {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    Payment persist(UUID orderId, UUID userId, BigDecimal amount, PaymentStatus status) {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status(status)
                .timestamp(Instant.now())
                .paymentAmount(amount)
                .build();
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent(saved.getId(), saved.getOrderId(), saved.getUserId(),
                saved.getStatus().name(), saved.getPaymentAmount());
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.PENDING)
                .orderId(saved.getOrderId())
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(Instant.now())
                .attempts(0)
                .build();
        outboxRepository.save(outboxEvent);

        return saved;
    }
}
