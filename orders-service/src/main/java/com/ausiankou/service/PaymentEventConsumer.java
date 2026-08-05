package com.ausiankou.service;

import com.ausiankou.dto.PaymentEvent;
import com.ausiankou.dto.PaymentStatus;
import com.ausiankou.entity.Order;
import com.ausiankou.entity.OrderStatus;
import com.ausiankou.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Received payment event: orderId={}, status={}", event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId())
                .orElse(null);

        if (order == null) {
            log.warn("Order not found for payment event: orderId={}", event.getOrderId());
            return;
        }

        if (event.getStatus() == PaymentStatus.SUCCESS) {
            order.setStatus(OrderStatus.CONFIRMED);  // ✅ Используем CONFIRMED вместо PAID
            log.info("Order {} confirmed after successful payment", event.getOrderId());

        } else if (event.getStatus() == PaymentStatus.FAILED) {
            order.setStatus(OrderStatus.CANCELLED);  // ✅ Используем CANCELLED вместо PAYMENT_FAILED
            log.info("Order {} cancelled due to payment failure", event.getOrderId());
        }

        orderRepository.save(order);
    }
}
