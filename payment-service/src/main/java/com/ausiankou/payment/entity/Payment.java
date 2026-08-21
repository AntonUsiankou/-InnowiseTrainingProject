package com.ausiankou.payment.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Document(collection = "payments")
@CompoundIndex(name = "idx_order_user", def = "{'orderId': 1, 'userId': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    private UUID id;

    @Indexed
    private UUID orderId;

    @Indexed
    private UUID userId;

    @Indexed
    private PaymentStatus status;

    @CreatedDate
    private Instant timestamp;

    private BigDecimal paymentAmount;
}
