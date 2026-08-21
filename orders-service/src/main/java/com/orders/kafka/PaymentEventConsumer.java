package com.orders.kafka;

import com.orders.entity.OrderStatus;
import com.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes CREATE_PAYMENT events published by Payment Service and updates the
 * corresponding order's status. Uses a manual/at-least-once ack strategy
 * (see application.yml) with idempotent status update so re-delivery is safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "${kafka.topics.create-payment}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onPaymentEvent(PaymentEvent event) {
        log.info("Received CREATE_PAYMENT event for order={} status={}", event.orderId(), event.status());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            OrderStatus newStatus = "SUCCESS".equalsIgnoreCase(event.status())
                    ? OrderStatus.PAID
                    : OrderStatus.FAILED;
            order.setStatus(newStatus);
        }, () -> log.warn("Order {} not found for incoming payment event", event.orderId()));
    }
}
