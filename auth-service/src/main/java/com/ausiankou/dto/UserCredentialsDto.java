package com.ausiankou.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCredentialsDto {
    private String email;
    private String role;
    private Long userId;
    private boolean enabled;
}
