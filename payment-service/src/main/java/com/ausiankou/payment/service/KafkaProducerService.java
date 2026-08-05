package com.ausiankou.payment.service;

import com.ausiankou.payment.dto.PaymentEvent;
import com.ausiankou.payment.dto.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${kafka.topic.payment-events:payment-events}")
    private String paymentEventsTopic;

    @Value("${kafka.topic.payment-events-dlq:payment-events-dlq}")
    private String deadLetterTopic;

    /**
     * Send payment event with retry and backoff
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public CompletableFuture<SendResult<String, PaymentEvent>> sendPaymentEvent(
            String paymentId, Long orderId, Long userId, PaymentStatus status) {

        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .orderId(orderId)
                .userId(userId)
                .status(status)
                .timestamp(LocalDateTime.now())
                .eventType("CREATE_PAYMENT")
                .build();

        log.info("Sending payment event to Kafka: orderId={}, status={}", orderId, status);

        // Send asynchronously with timeout
        CompletableFuture<SendResult<String, PaymentEvent>> future =
                kafkaTemplate.send(paymentEventsTopic, orderId.toString(), event)
                        .orTimeout(5, TimeUnit.SECONDS);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send payment event to Kafka: {}", ex.getMessage(), ex);
                sendToDeadLetterQueue(event, ex.getMessage());
            } else {
                log.info("Payment event sent successfully: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Send failed events to Dead Letter Queue
     */
    private void sendToDeadLetterQueue(PaymentEvent event, String errorMessage) {
        event.setEventType("DEAD_LETTER_" + event.getEventType());

        kafkaTemplate.send(deadLetterTopic, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send to DLQ as well: {}", ex.getMessage());
                        // Log to database as last resort
                        logToDatabase(event, errorMessage);
                    } else {
                        log.info("Event moved to DLQ: orderId={}", event.getOrderId());
                    }
                });
    }

    private void logToDatabase(PaymentEvent event, String errorMessage) {
        // In production, save to a separate collection for manual intervention
        log.error("CRITICAL: Event not deliverable - orderId: {}, error: {}",
                event.getOrderId(), errorMessage);
    }
}
