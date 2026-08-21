package com.ausiankou.user.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PaymentCardCreateRequest(
        @NotBlank String number,
        @NotBlank String holder,
        @NotNull @Future LocalDate expirationDate
) {}
