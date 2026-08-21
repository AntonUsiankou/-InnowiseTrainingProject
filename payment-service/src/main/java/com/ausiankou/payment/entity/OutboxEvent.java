package com.ausiankou.payment.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox: instead of publishing to Kafka directly from the
 * request thread (fire-and-forget - lost forever if Kafka is down when the
 * in-flight send finally fails), we write the event to this collection in
 * the SAME database as the Payment document, then a separate scheduled
 * poller (OutboxPublisher) relays PENDING rows to Kafka and only marks them
 * PUBLISHED once the broker has acked. If the broker is down, rows simply
 * stay PENDING and get retried on the next poll - nothing is lost as long
 * as the Payment DB itself doesn't lose data (which is what commit durability
 * is for).
 */
@Document(collection = "payment_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    private UUID id;

    @Indexed
    private OutboxStatus status;

    private UUID orderId; // used as the Kafka partition key

    /** JSON-serialized PaymentEvent payload. */
    private String payload;

    @CreatedDate
    private Instant createdAt;

    private int attempts;

    private Instant lastAttemptAt;
}
