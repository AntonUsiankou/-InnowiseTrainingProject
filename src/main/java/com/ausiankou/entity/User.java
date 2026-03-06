package com.ausiankou.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users",
        indexes = {
                @Index(name = "idx_user_email", columnList = "email"),
                @Index(name = "idx_user_active", columnList = "active"),
                @Index(name = "idx_user_birth_date", columnList = "birth_date")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,
            length = 100)
    private String name;

    @Column(nullable = false,
            length = 100)
    private String surname;

    @Column(name = "birth_date",
            nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false,
            unique = true,
            length = 255)
    private String email;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "user",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    private List<PaymentCard> paymentCards = new ArrayList<>();
    public void addPaymentCard(PaymentCard card){
        if(paymentCards.size()>=5){
            throw new IllegalStateException("Пользователь не должен иметь больше 5 карт");
        }
        paymentCards.add(card);
        card.setUser(this);
    }
}
