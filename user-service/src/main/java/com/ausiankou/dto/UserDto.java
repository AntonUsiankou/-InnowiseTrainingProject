package com.ausiankou.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;

    @NotBlank(message = "Имя обязательно для заполнения", groups = {Create.class, Update.class})
    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    @Pattern(
            regexp = "^[A-Za-zА-Яа-яёЁ]+$",
            message = "Поле должно содержать только буквы без пробелов и спецсимволов"
    )
    private String name;

    @NotBlank(message = "Фамилия обязательна для заполнения", groups = {Create.class, Update.class})
    @Size(min = 2, max = 100, message = "Фамилия должна быть от 2 до 100 символов")
    @Pattern(
            regexp = "^[A-Za-zА-Яа-яёЁ]+$",
            message = "Поле должно содержать только буквы без пробелов и спецсимволов"
    )
    private String surname;

    @NotNull(message = "Дата рождения обязательна", groups = {Create.class, Update.class})
    private LocalDate birthDate;

    @NotBlank(message = "Email обязателен для заполнения", groups = {Create.class, Update.class})
    @Email(message = "Некорректный формат email")
    @Size(max = 255)
    private String email;

    private Boolean active;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updateAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<PaymentCardDto> paymentCards;

    public interface Create {}
    public interface Update {}
}
