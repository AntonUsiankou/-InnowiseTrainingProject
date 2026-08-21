package com.ausiankou.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credentials", indexes = {
        @Index(name = "idx_credentials_login", columnList = "login", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Matches the id of the corresponding user profile in User Service. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String login;

    /** BCrypt hash - salt is embedded by BCrypt itself (unique per password). */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (role == null) {
            role = Role.USER;
        }
        enabled = true;
    }
}
