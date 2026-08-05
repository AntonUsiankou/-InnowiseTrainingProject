package com.ausiankou.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String id;

    @Indexed
    @Field("order_id")
    private Long orderId;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Indexed
    @Field("status")
    private String status;

    @Field("timestamp")
    private LocalDateTime timestamp;

    @Field("payment_amount")
    private BigDecimal paymentAmount;
}
