package com.ausiankou.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardDto {

    private Long id;
    private Long userId;

    @NotBlank(message = "Номер карты обязателен для заполнения", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[0-9]{16}$", message = "Номер карты должен состоять из 16 цифр")
    private String number;

    @NotBlank(message = "Имя держателя обязательно для заполнения", groups = {Create.class, Update.class})
    @Size(min = 2, max = 255, message = "Имя держателя должно быть от 2 до 255 символов")
    @Pattern(
            regexp = "^[A-Za-z\\s]+$",
            message = "Имя держателя должно быть написано латиницей"
    )
    private String holder;

    @NotNull(message = "Дата окончания срока действия обязательна", groups = {Create.class, Update.class})
    @Future(message = "Срок действия карты должен быть в будущем")
    private LocalDate expirationDate;

    @NotNull(message = "Статус активности обязателен", groups = {Create.class, Update.class})
    private Boolean active;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updateAt;

    private UserDto user;

    public interface Create{}
    public interface Update{}
}
