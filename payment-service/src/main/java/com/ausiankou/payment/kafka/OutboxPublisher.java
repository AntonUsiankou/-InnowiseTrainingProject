package com.ausiankou.payment.kafka;

import com.ausiankou.payment.entity.OutboxEvent;
import com.ausiankou.payment.entity.OutboxStatus;
import com.ausiankou.payment.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Relays PENDING rows written by PaymentService (in the same DB transaction
 * as the Payment document itself - see MongoTransactionConfig) to Kafka.
 * This is the second half of the transactional outbox pattern: as long as
 * the Payment DB write succeeded, the event WILL eventually reach Kafka,
 * even if the broker was down at the moment of payment creation - it just
 * stays PENDING and gets picked up on a later poll instead of being lost.
 *
 * Runs on a fixed delay (not fixed rate) so a slow Kafka doesn't cause
 * overlapping polls to pile up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.create-payment}")
    private String topic;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            PaymentEvent payload = objectMapper.readValue(event.getPayload(), PaymentEvent.class);

            // Synchronous send here (bounded by the Kafka client's own
            // delivery.timeout.ms) is intentional: we need to know the
            // outcome before deciding whether to mark this row PUBLISHED or
            // leave it PENDING for the next poll. The scheduled poller
            // thread is separate from request-handling threads, so blocking
            // here doesn't affect API concurrency/throughput at all.
            kafkaTemplate.send(topic, event.getOrderId().toString(), payload).get();

            event.setStatus(OutboxStatus.PUBLISHED);
            outboxRepository.save(event);
        } catch (Exception ex) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event {} for order {} failed after {} attempts - needs manual attention",
                        event.getId(), event.getOrderId(), event.getAttempts(), ex);
            } else {
                log.warn("Outbox event {} for order {} failed (attempt {}/{}), will retry next poll",
                        event.getId(), event.getOrderId(), event.getAttempts(), MAX_ATTEMPTS, ex);
            }
            outboxRepository.save(event);
        }
    }
}
