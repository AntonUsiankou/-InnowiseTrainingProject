package com.ausiankou.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
public class ValidateTokenResponse {
    private boolean valid;
    private String message;
    private Long userId;
    private String email;
    private String role;
}
