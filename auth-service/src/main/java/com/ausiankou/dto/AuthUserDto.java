package com.ausiankou.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserDto {
    private Long Id;
    private String email;
    private String password;
    private String role;
    private Boolean active;
}
