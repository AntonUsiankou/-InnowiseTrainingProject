package com.ausiankou.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record UserCreateRequest(
        @NotBlank String name,
        @NotBlank String surname,
        @Past LocalDate birthDate,
        @NotBlank @Email String email
) {}
