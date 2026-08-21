package com.ausiankou.user.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentCardDto(
        UUID id,
        UUID userId,
        String number,
        String holder,
        LocalDate expirationDate,
        boolean active
) implements Serializable {}
