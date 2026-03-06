package com.ausiankou.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "payment_cards",
        indexes = {
                @Index(name = "idx_payment_card_user_id", columnList = "user_id"),
                @Index(name = "idx_payment_card_number", columnList = "number"),
                @Index(name = "idx_payment_card_active", columnList = "active")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 16)
    private String number;

    @Column(nullable = false, length = 255)
    private String holder;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(nullable = false)
    private Boolean active = true;
}
