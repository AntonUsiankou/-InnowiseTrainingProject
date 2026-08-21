package com.ausiankou.user.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        boolean active,
        List<PaymentCardDto> cards,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {}
